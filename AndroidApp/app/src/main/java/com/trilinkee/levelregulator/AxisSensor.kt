package com.trilinkee.levelregulator

class AxisSensor {
    public class RingBuffer {
        val maxSize: Int = 5
        var mBuffer: DoubleArray = DoubleArray(maxSize)
        var mInputPosition: Int = 0
        var mInitialized: Boolean = false
        public fun putData(data: Double) {
            if(mInitialized == false) {
                mInitialized = true
                for(i in 0 until maxSize) {
                    mBuffer[i] = data
                }
            }
            else {
                mBuffer[mInputPosition] = data
                mInputPosition = (mInputPosition + 1) % maxSize
            }
        }
        public fun getAverage(): Double {
            var i: Int = (mInputPosition + 1) % maxSize
            var average: Double = mBuffer[mInputPosition]
            while(i != mInputPosition) {
                average += mBuffer[i]
                i = (i + 1) % maxSize
            }
            return average / maxSize
        }
    }
    public class Sensor {
        public var mX: Double = 0.0
        public var mRingBufferX: RingBuffer = RingBuffer()
        public var mY: Double = 0.0
        public var mRingBufferY: RingBuffer = RingBuffer()
        public var mZ: Double = 0.0
        public var mRingBufferZ: RingBuffer = RingBuffer()
    }

    public var mAcceleration: Sensor = Sensor()
    public var mGyroscope: Sensor = Sensor()
    public var mMagnetic: Sensor = Sensor()
    public var mTemperature: Double = 0.0
}
