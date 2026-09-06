package gobby.utils.managers

import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.PartyEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.partyMessageRegex

enum class PartyRole { LEADER, MODERATOR, MEMBER }

data class PartyMember(val name: String, val role: PartyRole)

private typealias PartyHandler = (MatchResult) -> Unit

private const val RANK = """(?:\[[^]]+] )?"""
private const val NAME = """(\w{1,16})"""

private val ROSTER_ENTRY = Regex("""$RANK(\w{1,16}) ●""")
private val LISTED_NAME = Regex("""$RANK(\w{1,16})""")

object PartyManager {

    private val roster = linkedMapOf<String, PartyRole>()

    val members: List<PartyMember> get() = roster.map { PartyMember(it.key, it.value) }

    val leader: PartyMember? get() = members.firstOrNull { it.role == PartyRole.LEADER }

    val moderators: List<PartyMember> get() = members.filter { it.role == PartyRole.MODERATOR }

    val size: Int get() = roster.size

    val inParty: Boolean get() = roster.isNotEmpty()

    val isLeader: Boolean
        get() {
            val self = selfName ?: return false
            return leader?.name.equals(self, ignoreCase = true)
        }

    private val selfName: String? get() = mc.player?.gameProfile?.name

    private val handlers: List<Pair<Regex, PartyHandler>> = listOf(
        Regex("""^Party Members \(\d+\)$""") to { _ -> roster.clear() },
        Regex("""^Party Leader: $RANK$NAME ●$""") to { m -> put(m.name, PartyRole.LEADER) },
        Regex("""^Party Moderators: (.+)$""") to { m -> putListed(m.groupValues[1], PartyRole.MODERATOR) },
        Regex("""^Party Members: (.+)$""") to { m -> putListed(m.groupValues[1], PartyRole.MEMBER) },

        Regex("""^You have joined $RANK$NAME's? party!$""") to { m -> joinParty(m.name) },
        Regex("""^You'll be partying with: (.+)$""") to { m -> addListed(m.groupValues[1]) },
        Regex("""^$RANK$NAME joined the party\.$""") to { m -> addMember(m.name) },
        Regex("""^Party Finder > $RANK$NAME joined the (?:dungeon )?group! \(\w+ Level \d+\)$""") to
            { m -> addMember(m.name) },
        Regex("""^The party leader $RANK$NAME has rejoined\.$""") to { m -> promoteToLeader(m.name) },
        Regex("""^$RANK$NAME has rejoined\.$""") to { m -> putIfAbsent(m.name) },

        Regex("""^$RANK$NAME has left the party\.$""") to { m -> removeMember(m.name) },
        Regex("""^$RANK$NAME has been removed from the party\.$""") to { m -> removeMember(m.name) },
        Regex("""^$RANK$NAME was removed from your party because they disconnected\.$""") to
            { m -> removeMember(m.name) },
        Regex("""^Kicked $RANK$NAME because they were offline\.$""") to { m -> removeMember(m.name) },

        Regex("""^The party was transferred to $RANK$NAME by $RANK$NAME$""") to { m -> promoteToLeader(m.name) },
        Regex("""^The party was transferred to $RANK$NAME because $RANK$NAME left$""") to { m ->
            removeMember(m.groupValues[2])
            promoteToLeader(m.name)
        },

        Regex("""^The party leader, $RANK$NAME has disconnected, they have 5 minutes to rejoin before the party is disbanded\.$""") to
            { m -> promoteToLeader(m.name) },
        Regex("""^$RANK$NAME invited $RANK\w{1,16} to the party! They have 60 seconds to accept\.$""") to
            { m -> invitedBy(m.name) },
        Regex("""^Party Leader, $RANK$NAME, summoned you to their server\.$""") to { m -> promoteToLeader(m.name) },
        Regex("""^(?:You are not this party's leader!|Only the party leader may join the queue\.)$""") to
            { _ -> demoteSelf() },
        partyMessageRegex to { m -> putIfAbsent(m.groupValues[2]) },

        Regex("""^Attempting to add you to the party\.\.\.$""") to { _ -> roster.clear() },
        Regex("""^You left the party\.$""") to { _ -> disband() },
        Regex("""^You have been kicked from the party by $RANK$NAME\s*$""") to { _ -> disband() },
        Regex("""^$RANK$NAME has disbanded the party!$""") to { _ -> disband() },
        Regex("""^The party was disbanded because .+$""") to { _ -> disband() },
        Regex("""^You are not (?:currently )?in a party\b.*$""") to { _ -> roster.clear() }
    )

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        handlers.forEach { (pattern, handle) ->
            pattern.matchEntire(event.message)?.let {
                handle(it)
                return
            }
        }
    }

    private val MatchResult.name: String get() = groupValues[1]

    private fun publish(event: PartyEvent) = Gobbyclient.EVENT_MANAGER.publish(event)

    private fun put(name: String, role: PartyRole) {
        roster[name] = role
    }

    private fun putIfAbsent(name: String) {
        roster.putIfAbsent(name, PartyRole.MEMBER)
    }

    private fun putListed(payload: String, role: PartyRole) =
        ROSTER_ENTRY.findAll(payload).forEach { put(it.name, role) }

    private fun addListed(payload: String) =
        payload.split(", ").forEach { entry -> LISTED_NAME.matchEntire(entry.trim())?.let { putIfAbsent(it.name) } }

    private fun addMember(name: String) {
        if (roster.put(name, PartyRole.MEMBER) != null) return
        publish(PartyEvent.Join(PartyMember(name, PartyRole.MEMBER)))
    }

    private fun removeMember(name: String) {
        val role = roster.remove(name) ?: return
        publish(PartyEvent.Leave(PartyMember(name, role)))
    }

    private fun disband() {
        if (roster.isEmpty()) return
        roster.clear()
        publish(PartyEvent.Disband)
    }

    private fun promoteToLeader(name: String) {
        if (roster[name] == PartyRole.LEADER) return
        roster.replaceAll { _, role -> if (role == PartyRole.LEADER) PartyRole.MEMBER else role }
        put(name, PartyRole.LEADER)
        publish(PartyEvent.LeaderChanged(PartyMember(name, PartyRole.LEADER)))
    }

    private fun joinParty(leaderName: String) {
        roster.clear()
        put(leaderName, PartyRole.LEADER)
        selfName?.let(::putIfAbsent)
    }

    private fun invitedBy(inviter: String) {
        if (roster.isEmpty() && inviter.equals(selfName, ignoreCase = true)) promoteToLeader(inviter)
        else putIfAbsent(inviter)
    }

    private fun demoteSelf() {
        val self = selfName ?: return
        if (roster[self] == PartyRole.LEADER) put(self, PartyRole.MEMBER)
    }
}
