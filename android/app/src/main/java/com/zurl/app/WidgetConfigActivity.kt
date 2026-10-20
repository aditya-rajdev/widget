package com.zurl.app

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.util.ArrayList

class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val WIDGET_DATA_PREFS = "widget_data"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)
        val listView = findViewById<ListView>(R.id.configListView)

        val sharedPref = getSharedPreferences(WIDGET_DATA_PREFS, Context.MODE_PRIVATE)
        val jsonStr = sharedPref.getString("links", "[]") ?: "[]"
        
        val linksList = ArrayList<LinkData>()

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                linksList.add(
                    LinkData(
                        title = obj.optString("title", "No Title"),
                        url = obj.optString("url", ""),
                        customImage = obj.optString("customImage", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (linksList.isEmpty()) {
            Toast.makeText(this, "Pehle app me links add karein!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val configAdapter = IntelligentConfigAdapter(this, linksList)
        listView.adapter = configAdapter

        listView.setOnItemClickListener { _, clickedView, position, _ ->
            val selectedItem = linksList[position]
            val popup = PopupMenu(this@WidgetConfigActivity, clickedView)
            
            popup.menu.add(0, 1, 0, "Default (Brand Color)")
            popup.menu.add(0, 2, 1, "Aesthetic (Custom Color)")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    2 -> {
                        showAdvancedVisualColorWheelDialog(selectedItem)
                    }
                    else -> {
                        deployWidgetChanges(selectedItem, null)
                    }
                }
                true
            }
            popup.show()
        }
    }

    /**
     * Programmatic custom wheel dialog box trigger system
     */
    private fun showAdvancedVisualColorWheelDialog(selectedItem: LinkData) {
        val builder = AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        var bufferSelectedColorHex = "#FFFFFF"
        val hexValueTextView = TextView(this).apply {
            text = "HEX: $bufferSelectedColorHex"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 32)
            gravity = android.view.Gravity.CENTER
        }
        rootLayout.addView(hexValueTextView)

        val density = resources.displayMetrics.density
        val wheelSize = (240 * density).toInt()
        
        val colorWheelView = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var colorBitmap: Bitmap? = null
            var markerX = 0f
            var markerY = 0f
            var isFirstDraw = true

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0 && h > 0) {
                    colorBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(colorBitmap!!)
                    val centerX = w / 2f
                    val centerY = h / 2f
                    val radius = Math.min(centerX, centerY) - (15 * density)

                    val colors = intArrayOf(
                        Color.RED, Color.DKGRAY, Color.MAGENTA, Color.BLUE, 
                        Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
                    )
                    paint.shader = SweepGradient(centerX, centerY, colors, null)
                    canvas.drawCircle(centerX, centerY, radius, paint)

                    val innerColors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
                    paint.shader = android.graphics.RadialGradient(centerX, centerY, radius, innerColors, null, android.graphics.Shader.TileMode.CLAMP)
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    if (isFirstDraw) {
                        markerX = centerX
                        markerY = centerY
                        isFirstDraw = false
                    }
                }
            }

            override fun onDraw(canvas: Canvas) {
                colorBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                
                paint.shader = null
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3 * density
                canvas.drawCircle(markerX, markerY, 12 * density, paint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (colorBitmap == null) return false
                
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()

                    if (x >= 0 && x < colorBitmap!!.width && y >= 0 && y < colorBitmap!!.height) {
                        val pixel = colorBitmap!!.getPixel(x, y)
                        if (Color.alpha(pixel) > 0) {
                            markerX = event.x
                            markerY = event.y
                            bufferSelectedColorHex = String.format("#%06X", 0xFFFFFF and pixel)
                            
                            hexValueTextView.text = "HEX: $bufferSelectedColorHex"
                            hexValueTextView.setTextColor(pixel)
                            invalidate()
                        }
                    }
                }
                return true
            }
        }
        
        colorWheelView.layoutParams = LinearLayout.LayoutParams(wheelSize, wheelSize)
        rootLayout.addView(colorWheelView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 32, 0, 0)
        }

        val dialog = builder.setView(rootLayout).create()

        val btnCancel = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.GRAY)
            background = null
            setOnClickListener { dialog.dismiss() }
        }

        val btnApply = Button(this).apply {
            text = "Apply"
            setTextColor(Color.WHITE)
            background = null
            setOnClickListener {
                deployWidgetChanges(selectedItem, bufferSelectedColorHex)
                dialog.dismiss()
            }
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnApply)
        rootLayout.addView(buttonLayout)

        dialog.show()
    }

    private fun deployWidgetChanges(selectedItem: LinkData, chosenColorHex: String?) {
        val context = this@WidgetConfigActivity
        val lowerUrl = selectedItem.url.lowercase()

        val prefs = context.getSharedPreferences(WIDGET_DATA_PREFS, Context.MODE_PRIVATE).edit()
        prefs.putString("widget_title_$appWidgetId", selectedItem.title)
        prefs.putString("widget_url_$appWidgetId", selectedItem.url)
        prefs.putString("widget_img_$appWidgetId", selectedItem.customImage)
        
        if (chosenColorHex != null) {
            prefs.putString("widget_custom_color_$appWidgetId", chosenColorHex)
        } else {
            prefs.remove("widget_custom_color_$appWidgetId")
        }
        prefs.apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val isThreeCell = widgetInfo?.provider?.className?.contains("ThreeCellWidgetProvider") == true

        val iconRes = when {
            lowerUrl.contains("youtube") || lowerUrl.contains("youtu.be") -> R.drawable.youtube
            lowerUrl.contains("github") -> R.drawable.github
            lowerUrl.contains("linkedin") -> R.drawable.linkedin
            lowerUrl.contains("leetcode") -> R.drawable.leetcode
            lowerUrl.contains("instagram") -> R.drawable.instagram
            else -> R.drawable.link
        }

        val defaultBgColor = when {
            lowerUrl.contains("youtube") || lowerUrl.contains("youtu.be") -> android.graphics.Color.parseColor("#de0000")
            lowerUrl.contains("github") -> android.graphics.Color.parseColor("#000000")
            lowerUrl.contains("linkedin") -> android.graphics.Color.parseColor("#006de1")
            lowerUrl.contains("leetcode") -> android.graphics.Color.parseColor("#ec9c08")
            lowerUrl.contains("instagram") -> android.graphics.Color.parseColor("#df088d")
            else -> android.graphics.Color.parseColor("#1A1A1A")
        }

        val finalBgColor = if (chosenColorHex != null) {
            android.graphics.Color.parseColor(chosenColorHex)
        } else {
            defaultBgColor
        }

        if (isThreeCell) {
            val views = RemoteViews(context.packageName, R.layout.three_by_one_widget_layout)
            views.setTextViewText(R.id.threeCellWidgetTitle, selectedItem.title)
            views.setImageViewResource(R.id.threeCellWidgetIcon, iconRes)
            views.setInt(R.id.threeCellWidgetIcon, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            views.setInt(R.id.threeCellWidgetRoot, "setBackgroundColor", finalBgColor)

            if (chosenColorHex == "#FFFFFF") {
                views.setTextColor(R.id.threeCellWidgetTitle, android.graphics.Color.BLACK)
            } else {
                views.setTextColor(R.id.threeCellWidgetTitle, android.graphics.Color.WHITE)
            }

            val clickIntent = Intent(context, LinkClickReceiver::class.java).apply {
                action = "com.zurl.app.ACTION_OPEN_URL"
                putExtra("EXTRA_URL", selectedItem.url)
                data = Uri.parse("study://threecell/$appWidgetId")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.threeCellWidgetRoot, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } else {
            val views = RemoteViews(context.packageName, R.layout.one_cell_widget_layout)
            views.setViewVisibility(R.id.userCustomImageView, View.GONE)
            views.setViewVisibility(R.id.defaultWidgetLayout, View.VISIBLE)
            views.setTextViewText(R.id.oneCellWidgetTitle, selectedItem.title)
            views.setImageViewResource(R.id.oneCellWidgetIcon, iconRes)
            
            views.setInt(R.id.oneCellWidgetIcon, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            views.setInt(R.id.oneCellWidgetRoot, "setBackgroundColor", finalBgColor)

            if (chosenColorHex == "#FFFFFF") {
                views.setTextColor(R.id.oneCellWidgetTitle, android.graphics.Color.BLACK)
            } else {
                views.setTextColor(R.id.oneCellWidgetTitle, android.graphics.Color.WHITE)
            }

            val clickIntent = Intent(context, LinkClickReceiver::class.java).apply {
                action = "com.zurl.app.ACTION_OPEN_URL"
                putExtra("EXTRA_URL", selectedItem.url)
                data = Uri.parse("study://onecell/$appWidgetId")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.oneCellWidgetRoot, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            component = if (isThreeCell) {
                ComponentName(context, ThreeCellWidgetProvider::class.java)
            } else {
                ComponentName(context, OneCellWidgetProvider::class.java)
            }
        }
        context.sendBroadcast(updateIntent)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    data class LinkData(val title: String, val url: String, val customImage: String)

    private class IntelligentConfigAdapter(val context: Context, val items: List<LinkData>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.widget_config_item, parent, false)
            
            val item = items[position]
            val iconView = view.findViewById<ImageView>(R.id.configItemIcon)
            val titleView = view.findViewById<TextView>(R.id.configItemTitle)
            val subView = view.findViewById<TextView>(R.id.configItemSub)

            titleView.text = item.title
            val cleanedSubText = item.url.replace("https://", "").replace("http://", "").split("/")[0]
            subView.text = cleanedSubText

            val lowerUrl = item.url.lowercase()
            val iconRes = when {
                lowerUrl.contains("youtube") || lowerUrl.contains("youtu.be") -> R.drawable.youtube
                lowerUrl.contains("github") -> R.drawable.github
                lowerUrl.contains("linkedin") -> R.drawable.linkedin
                lowerUrl.contains("leetcode") -> R.drawable.leetcode
                lowerUrl.contains("instagram") -> R.drawable.instagram
                else -> R.drawable.link
            }

            if (!item.customImage.isNullOrBlank()) {
                iconView.setImageURI(Uri.parse(item.customImage))
            } else {
                iconView.setImageResource(iconRes)
            }

            return view
        }
    }
}