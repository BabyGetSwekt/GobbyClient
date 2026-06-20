package gobby.interfaces;

public interface EspLayerHidingState {
    void gobbyclient$setHideLayers(boolean hide);

    boolean gobbyclient$shouldHideLayers();

    void gobbyclient$setHideBody(boolean hide);

    boolean gobbyclient$shouldHideBody();
}
