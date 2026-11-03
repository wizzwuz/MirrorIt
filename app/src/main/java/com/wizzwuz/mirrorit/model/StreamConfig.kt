package com.wizzwuz.mirrorit.model

data class StreamConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val bitRate: Int = 4_000_000, // 4 Mbps
    val iFrameIntervalSeconds: Int = 1
) {
    companion object {
        val PRESET_FHD = StreamConfig(width = 1920, height = 1080, frameRate = 60, bitRate = 8_000_000)
        val PRESET_HD = StreamConfig(width = 1280, height = 720, frameRate = 30, bitRate = 4_000_000)
        val PRESET_SD = StreamConfig(width = 854, height = 480, frameRate = 30, bitRate = 2_000_000)
    }
}
