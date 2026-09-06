package gobby.events

import gobby.utils.managers.PartyMember

sealed class PartyEvent : Events() {
    class Join(val member: PartyMember) : PartyEvent()
    class Leave(val member: PartyMember) : PartyEvent()
    class LeaderChanged(val leader: PartyMember) : PartyEvent()
    object Disband : PartyEvent()
}
