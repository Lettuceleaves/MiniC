package minic.runtime.step;

/**
 * 编译观测播放模式。
 */
public enum PlaybackMode {
    /**
     * 暂停状态。
     */
    PAUSED,

    /**
     * 自动播放，默认 1000ms/帧。
     */
    PLAYING,

    /**
     * 两倍速自动播放，默认 500ms/帧。
     */
    FAST_PLAYING
}
