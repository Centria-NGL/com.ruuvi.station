package com.ruuvi.tag.feature

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.v7.app.AppCompatActivity
import com.ruuvi.tag.R
import com.ruuvi.tag.model.RuuviTag
import com.ruuvi.tag.scanning.RuuviTagListener
import com.ruuvi.tag.scanning.RuuviTagScanner
import com.ruuvi.tag.util.Utils

import kotlinx.android.synthetic.main.activity_tag_details.*
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.*
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.widget.*


class TagDetails : AppCompatActivity(), RuuviTagListener {
    var tag: RuuviTag? = null
    var tags: List<RuuviTag>? = null

    var scanner: RuuviTagScanner? = null
    var tempText: TextView? = null
    var humidityText: TextView? = null
    var pressureText: TextView? = null
    var signalText: TextView? = null
    var updatedText: TextView? = null

    var pager: ViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_details)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = null
        supportActionBar?.setIcon(R.drawable.logo)

        tempText = findViewById(R.id.tag_temp)
        humidityText = findViewById(R.id.tag_humidity)
        pressureText = findViewById(R.id.tag_pressure)
        signalText = findViewById(R.id.tag_signal)
        updatedText = findViewById(R.id.tag_updated)
        pager = findViewById(R.id.tag_pager)

        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        pager?.pageMargin = - (size.x / 2)
        pager!!.setOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                tag = tags!!.get(position)
                updateUI()
            }
        })

        val tagId = intent.getStringExtra("id");
        tags = RuuviTag.getAll()
        val pagerAdapter = TagPager(tags!!)
        pager?.adapter = pagerAdapter

        for (i in tags!!.indices) {
            if (tags!!.get(i).id == tagId) {
                tag = tags!!.get(i)
                pager!!.currentItem = i
            }
        }

        if (tag == null) {
            Toast.makeText(this, "Something went wrong..", Toast.LENGTH_SHORT).show()
            finish()
        }

        updateUI()

        scanner = RuuviTagScanner(this, this)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            finish()
        } else if (item?.itemId == R.id.action_share) {
            if (tag?.url != null) {
                if (!tag!!.url.isEmpty()) {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_TEXT, tag!!.url!!)
                    this.startActivity(shareIntent)
                    return true;
                }
            }
            Toast.makeText(this,
                    "You can only share tags in weather station mode right now"
                    , Toast.LENGTH_SHORT).show()
        } else {
            showOptionsMenu()
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        tags = RuuviTag.getAll()
        scanner?.start()
    }

    override fun onPause() {
        super.onPause()
        scanner?.stop()
    }

    override fun tagFound(tag: RuuviTag) {
        if (tag.id == this.tag?.id) {
            this.tag?.updateDataFrom(tag);
            this.tag?.update()
            updateUI()
        }
    }

    fun updateUI() {
        tag?.let {
            tempText?.text = String.format(this.getString(R.string.temperature_reading), tag?.temperature)
            humidityText?.text = String.format(this.getString(R.string.humidity_reading), tag?.humidity)
            pressureText?.text = String.format(this.getString(R.string.pressure_reading), tag?.pressure)
            signalText?.text = String.format(this.getString(R.string.signal_reading), tag?.rssi)
            var updatedAt = this.resources.getString(R.string.updated) + " " + Utils.strDescribingTimeSince(tag?.updateAt);
            updatedText?.text = updatedAt
        }
    }

    private fun showOptionsMenu() {
        val sheetDialog = BottomSheetDialog(this)
        var listView = ListView(this)
        val menu: List<String> = this.resources.getStringArray(R.array.station_tag_menu).toList()

        listView.adapter = ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                menu
        )

        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            when (i) {
                0 -> {
                    val intent = Intent(this, TagSettings::class.java)
                    intent.putExtra(TagSettings.TAG_ID, tag?.id)
                    this.startActivity(intent)
                }
                1 -> {
                    Toast.makeText(this,
                            "Currently broken",
                            Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    delete()
                }
            }

            sheetDialog.dismiss()
        }

        sheetDialog.setContentView(listView)
        sheetDialog.show()
    }

    fun delete() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(this.getString(R.string.tag_delete_title))
        builder.setMessage(this.getString(R.string.tag_delete_message))
        builder.setPositiveButton(android.R.string.ok) { dialogInterface, i ->
            finish()
            tag?.deleteTagAndRelatives()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialogInterface, i -> }

        builder.show()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_details, menu)
        return true
    }
}

class TagPager constructor(tags: List<RuuviTag>) : PagerAdapter() {
    var tags = tags

    override fun instantiateItem(container: ViewGroup?, position: Int): Any {
        val context = container?.context!!

        val textView: TextView = TextView(context)
        textView.text = tags.get(position).dispayName
        textView.setTextColor(Color.WHITE)
        textView.gravity = Gravity.CENTER_HORIZONTAL
        textView.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        textView.textSize = context.resources.getDimension(R.dimen.tag_details_name)
        textView.setTypeface(null, Typeface.BOLD)

        (container as ViewPager).addView(textView, 0)
        return textView
    }

    override fun isViewFromObject(view: View?, `object`: Any?): Boolean {
        return view == `object`
    }

    override fun destroyItem(container: ViewGroup?, position: Int, `object`: Any?) {
        // hmm
    }

    override fun getCount(): Int {
        return tags.size
    }

}
