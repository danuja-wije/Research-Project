package com.example.geotag

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer


class MotionActivity() : AppCompatActivity(), SensorEventListener, Parcelable {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var brightnessLabel: TextView
    private lateinit var interpreter: Interpreter
    private var lastUpdateTime: Long = 0
    private val UPDATE_INTERVAL = 100

    constructor(parcel: Parcel,context: Context) : this() {
        lastUpdateTime = parcel.readLong()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motion)

        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        brightnessLabel = findViewById(R.id.brightnessLabel)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        brightnessSeekBar.max = 255
        brightnessSeekBar.progress = 0
        brightnessSeekBar.isEnabled = false
        val model = loadModelFile( "model")
        val interpreter = model?.let { Interpreter(it) }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    fun predict(inputData: FloatArray): FloatArray {
        val inputShape = interpreter.getInputTensor(0).shape()
        val inputDataType = interpreter.getInputTensor(0).dataType()
        val inputBuffer = TensorBuffer.createFixedSize(inputShape, inputDataType)
        inputBuffer.loadArray(inputData)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputDataType = interpreter.getOutputTensor(0).dataType()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType)

        interpreter.run(inputBuffer.buffer, outputBuffer.buffer)

        return outputBuffer.floatArray
    }
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                lastUpdateTime = currentTime
                
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

                val brightness = accelerationToBrightness(acceleration)

                runOnUiThread {
                    brightnessSeekBar.progress = brightness
                    brightnessLabel.text = "Brightness: $brightness"
                }
            }
        }
    }
//Loading Predictive model
private fun loadModelFile(modelFileName: String): MappedByteBuffer? {
    try {
        val assetFileDescriptor = assets.openFd(modelFileName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel

    }catch (e:Exception){
        Log.d("GeoTag", "Model loded error")
    }finally {
        return null
    }
}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
       
    }

    private fun accelerationToBrightness(acceleration: Float): Int {
        val minAcceleration = 0f
        val maxAcceleration = 12f // Adjust this value when testing

        val clampedAcceleration = acceleration.coerceIn(minAcceleration, maxAcceleration)

        return ((clampedAcceleration / maxAcceleration) * 255).toInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(lastUpdateTime)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MotionActivity> {
        override fun createFromParcel(parcel: Parcel): MotionActivity {
            return MotionActivity()
        }

        override fun newArray(size: Int): Array<MotionActivity?> {
            return arrayOfNulls(size)
        }
    }
}