package com.trilinkee.levelregulator

class ApplicationConfig {
    public var mLevelAdjustX: Double = 0.0
    public var mLevelAdjustY: Double = 0.0

    public var mFrontRearSwap: Boolean = false
    public var mLeftRightSwap: Boolean = false
    public var mAxisSwap: Boolean = false

    public fun reset() {
        mLevelAdjustX = 0.0
        mLevelAdjustY = 0.0

        mFrontRearSwap = false
        mLeftRightSwap = false
        mAxisSwap = false
    }
}