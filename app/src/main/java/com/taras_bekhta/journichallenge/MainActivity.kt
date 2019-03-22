package com.taras_bekhta.journichallenge

import android.graphics.*
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringWriter


class MainActivity : AppCompatActivity(), View.OnTouchListener {

    val countries = arrayListOf<Country>()

    private var minX = 0.0
    private var maxX = 0.0
    private var minY = 0.0
    private var maxY = 0.0

    private var matrix = Matrix()
    private var savedMatrix = Matrix()

    //STATES
    private val NONE = 0
    private val DRAG = 1
    private val ZOOM = 2
    private var mode = NONE

    private var start = PointF()
    private var mid = PointF()
    private var oldDist = 1f
    private var oldRot = 1f

    private var lastEvent: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        readAndParseJSON()
        drawMap()

        mapImageView.setOnTouchListener(this)
    }

    override fun onTouch(mapView: View, event: MotionEvent): Boolean {
        val scale: Float

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mapView.pivotX = event.x
                mapView.pivotY = event.y
                savedMatrix.set(matrix)
                start.set(event.x, event.y)
                mode = DRAG
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event).toFloat()
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
                lastEvent = FloatArray(4)
                lastEvent!![0] = event.getX(0)
                lastEvent!![1] = event.getX(1)
                lastEvent!![2] = event.getY(0)
                lastEvent!![3] = event.getY(1)
                oldRot = rotation(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }

            MotionEvent.ACTION_MOVE ->
                if (mode == DRAG) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(event.x - start.x, event.y - start.y)
                } else if (mode == ZOOM && event.pointerCount == 2) {
                    val newDist = spacing(event)
                    matrix.set(savedMatrix)
                    if (newDist > 10f) {
                        scale = newDist.toFloat() / oldDist
                        matrix.postScale(scale, scale, mid.x, mid.y)
                    }
                    if (lastEvent != null) {
                        val newRot = rotation(event)
                        val r = newRot - oldRot
                        matrix.postRotate(r,mapImageView.measuredWidth.toFloat() / 2,mapImageView.measuredWidth.toFloat() / 2)
                    }
                }
        }
        mapImageView.imageMatrix = matrix

        return true

    }

    private fun rotation(event: MotionEvent): Float {
        val delta_x = (event.getX(0) - event.getX(1)).toDouble()
        val delta_y = (event.getY(0) - event.getY(1)).toDouble()
        val radians = Math.atan2(delta_y, delta_x)

        return Math.toDegrees(radians).toFloat()
    }

    private fun spacing(event: MotionEvent): Double {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble())
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    private fun readAndParseJSON() {
        val inStream = resources.openRawResource(R.raw.countries_small)

        val writer = StringWriter()
        val buffer = CharArray(1024)
        inStream.use { inStream ->
            val reader = BufferedReader(InputStreamReader(inStream, "UTF-8"))
            var n: Int = reader.read(buffer)
            while (n != -1) {
                writer.write(buffer, 0, n)
                n = reader.read(buffer)
            }
        }

        val jsonString = writer.toString()
        val wholeMapObj = JSONObject(jsonString)
        val countriesJSON = wholeMapObj.getJSONArray("features")

        for (i in 0 until countriesJSON.length()) {
            val row = countriesJSON.getJSONObject(i)
            val newCountry = Country()
            val props = row.getJSONObject("properties")
            newCountry.name = props.getString("name")

            val geom = row.getJSONObject("geometry")
            val coordinates = geom.getJSONArray("coordinates")
            val type = geom.getString("type")

            if (type.toLowerCase() == "polygon") {
                val newCoordsParentArr = ArrayList<ArrayList<Pair<Double, Double>>>()
                for (j in 0 until coordinates.length()) {
                    val coordsRow = coordinates.getJSONArray(j)
                    val newCoords = ArrayList<Pair<Double, Double>>()

                    for (x in 0 until coordsRow.length()) {
                        var coords = coordsRow.getJSONArray(x)
                        if (coords.getDouble(0) < minX) {
                            minX = coords.getDouble(0)
                        }
                        else if (coords.getDouble(0) > maxX) {
                            maxX = coords.getDouble(0)
                        }
                        if (coords.getDouble(1) < minY) {
                            minY = coords.getDouble(1)
                        }
                        else if (coords.getDouble(1) > maxY) {
                            maxY = coords.getDouble(1)
                        }
                        newCoords.add(Pair(coords.getDouble(0), coords.getDouble(1)))
                    }
                    newCoordsParentArr.add(newCoords)
                }
                newCountry.coordinates = newCoordsParentArr
            } else {
                val newCoordsParentArr = ArrayList<ArrayList<Pair<Double, Double>>>()
                for (j in 0 until coordinates.length()) {
                    val firstArr = coordinates.getJSONArray(j)
                    for (x in 0 until firstArr.length()) {
                        val secondArr = firstArr.getJSONArray(x)
                        val newCoords = ArrayList<Pair<Double, Double>>()

                        for (t in 0 until secondArr.length()) {
                            var coords = secondArr.getJSONArray(t)
                            if (coords.getDouble(0) < minX) {
                                minX = coords.getDouble(0)
                            }
                            else if (coords.getDouble(0) > maxX) {
                                maxX = coords.getDouble(0)
                            }
                            if (coords.getDouble(1) < minY) {
                                minY = coords.getDouble(1)
                            }
                            else if (coords.getDouble(1) > maxY) {
                                maxY = coords.getDouble(1)
                            }
                            newCoords.add(Pair(coords.getDouble(0), coords.getDouble(1)))
                        }

                        newCoordsParentArr.add(newCoords)
                    }
                }
                newCountry.coordinates = newCoordsParentArr
            }

            countries.add(newCountry)
        }
    }

    private fun drawMap() {
        val bm = Bitmap.createBitmap(7000, 7000, Bitmap.Config.RGB_565)

        bm.eraseColor(Color.WHITE)
        val canvas = Canvas(bm)

        var scaleX = (maxX - minX) / 7000
        var scaleY = (maxY - minY) / 7000

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = Color.GRAY

        val border = Paint(Paint.ANTI_ALIAS_FLAG)
        border.style = Paint.Style.STROKE
        border.color = Color.YELLOW

        for (country in countries) {
            for (topArr in country.coordinates) {
                val path = Path()
                for (coords in topArr) {
                    if (path.isEmpty) {
                        path.moveTo(
                            (coords.first.toFloat() - minX.toFloat()) / scaleX.toFloat(),
                            (coords.second.toFloat() - minY.toFloat()) / scaleY.toFloat()
                        )
                    } else {
                        path.lineTo(
                            (coords.first.toFloat() - minX.toFloat()) / scaleX.toFloat(),
                            (coords.second.toFloat() - minY.toFloat()) / scaleY.toFloat()
                        )
                    }
                }

                canvas.drawPath(path, paint)
                canvas.drawPath(path, border)
            }
        }

        mapImageView.setImageBitmap(rotateBitmap(flipBitmap(bm), 180f))
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun flipBitmap(d: Bitmap): Bitmap {
        val m = Matrix()
        m.preScale(-1f, 1f)
        val src = d
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }
}
