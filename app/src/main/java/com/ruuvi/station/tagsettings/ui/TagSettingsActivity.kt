package com.ruuvi.station.tagsettings.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar
import com.flexsentlabs.androidcommons.app.ui.setDebouncedOnClickListener
import com.flexsentlabs.extensions.viewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ruuvi.station.BuildConfig
import com.ruuvi.station.R
import com.ruuvi.station.alarm.AlarmCheckInteractor
import com.ruuvi.station.database.tables.Alarm
import com.ruuvi.station.database.tables.RuuviTagEntity
import com.ruuvi.station.model.HumidityCalibration
import com.ruuvi.station.tagsettings.di.TagSettingsViewModelArgs
import com.ruuvi.station.util.CsvExporter
import com.ruuvi.station.util.Utils
import kotlinx.android.synthetic.main.activity_tag_settings.alertsContainerLayout
import kotlinx.android.synthetic.main.activity_tag_settings.calibrateHumidityButton
import kotlinx.android.synthetic.main.activity_tag_settings.inputMacTextView
import kotlinx.android.synthetic.main.activity_tag_settings.inputVoltageTextView
import kotlinx.android.synthetic.main.activity_tag_settings.rawValuesLayout
import kotlinx.android.synthetic.main.activity_tag_settings.removeTagButton
import kotlinx.android.synthetic.main.activity_tag_settings.tagImageCameraButton
import kotlinx.android.synthetic.main.activity_tag_settings.tagImageSelectButton
import kotlinx.android.synthetic.main.activity_tag_settings.tagImageView
import kotlinx.android.synthetic.main.activity_tag_settings.tagNameInputTextView
import kotlinx.android.synthetic.main.activity_tag_settings.toolbar
import kotlinx.android.synthetic.main.activity_tag_settings.xInputTextView
import kotlinx.android.synthetic.main.activity_tag_settings.yInputTextView
import kotlinx.android.synthetic.main.activity_tag_settings.zInputTextView
import kotlinx.android.synthetic.main.dialog_humidity_calibration.timestamp
import kotlinx.coroutines.flow.collect
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date

class TagSettingsActivity : AppCompatActivity(), KodeinAware {

    override val kodein: Kodein by closestKodein()

    private val viewModel: TagSettingsViewModel by viewModel {
        intent.getStringExtra(TAG_ID)?.let {
            TagSettingsViewModelArgs(it)
        }
    }

    private var alarmCheckboxListener = CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean ->
        val item = viewModel.alarmItems[buttonView.tag as Int]
        item.isChecked = isChecked
        item.updateView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tag_settings)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        lifecycleScope.launchWhenCreated {
            viewModel.tagFlow.collect { tag ->
                tag?.let {
                    isTagFavorite(it)
                    setupTagName(it)
                    setupInputMac(it)
                    setupTagImage(it)
                    calibrateHumidity(it)
                    updateReadings(it)
                }
            }
        }

        viewModel.tagAlarms = Alarm.getForTag(viewModel.tagId)

        setupAlarmItems()

        removeTagButton.setDebouncedOnClickListener { delete() }
    }

    override fun onPause() {
        super.onPause()

        viewModel.updateTag()
        saveOrUpdateAlarmItems()
    }

    @Suppress("NAME_SHADOWING")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            if (viewModel.file != null) {
                val rotation = getCameraPhotoOrientation(viewModel.file)
                resize(viewModel.file, rotation)
                viewModel.tag?.userBackground = viewModel.file.toString()
                val background = Utils.getBackground(applicationContext, viewModel.tag)
                tagImageView.setImageBitmap(background)
            }
        } else if (requestCode == REQUEST_GALLERY_PHOTO && resultCode == Activity.RESULT_OK) {
            data?.let {
                try {
                    val path = data.data
                    if (!isImage(path)) {
                        Toast.makeText(this, "File type not supported", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val inputStream = applicationContext.contentResolver.openInputStream(path!!)
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                    } catch (ex: IOException) {
                        // This is fine :)
                    }
                    if (photoFile != null) {
                        try {
                            photoFile.createNewFile()
                        } catch (ioEx: IOException) {
                            // :(
                            return
                        }
                        val output: OutputStream = FileOutputStream(photoFile)
                        output.use { output ->
                            val buffer = ByteArray(4 * 1024) // or other buffer size
                            var read: Int
                            while (inputStream!!.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                        val uri = Uri.fromFile(photoFile)
                        val rotation = getCameraPhotoOrientation(uri)
                        resize(uri, rotation)
                        viewModel.tag?.userBackground = uri.toString()
                        val background = Utils.getBackground(applicationContext, viewModel.tag)
                        tagImageView.setImageBitmap(background)
                    }
                } catch (e: Exception) {
                    // ... O.o
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_export) {
            val exporter = CsvExporter(this, viewModel.getRepositoryInstance())
            viewModel.tag?.id?.let {
                exporter.toCsv(it)
            }
        } else {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        viewModel.tag?.let {
            if (it.isFavorite) {
                menuInflater.inflate(R.menu.menu_edit, menu)
            }
        }
        return true
    }

    private fun isTagFavorite(tag: RuuviTagEntity) {
        if (!tag.isFavorite) {
            tag.isFavorite = true
            tag.createDate = Date()
            tag.update()
        }
    }

    private fun setupTagName(tag: RuuviTagEntity) {
        tagNameInputTextView.text = tag.displayName

        // TODO: 25/10/17 make this less ugly
        tagNameInputTextView.setDebouncedOnClickListener {
            val builder = AlertDialog.Builder(ContextThemeWrapper(this@TagSettingsActivity, R.style.AppTheme))

            builder.setTitle(getString(R.string.tag_name))

            val input = EditText(this@TagSettingsActivity)

            input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(32))

            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

            input.setText(tag.name)

            val container = FrameLayout(applicationContext)

            val params =
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            params.leftMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)

            params.rightMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)

            input.layoutParams = params

            container.addView(input)

            builder.setView(container)

            builder.setPositiveButton("Ok") { _: DialogInterface?, _: Int ->
                tag.name = input.text.toString()

                tagNameInputTextView.text = tag.name
            }

            builder.setNegativeButton("Cancel", null)

            val dialog: Dialog = builder.create()

            try {
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            } catch (e: Exception) {
                Timber.e(e, "Could not open keyboard")
            }
            dialog.show()

            input.requestFocus()
        }
    }

    private fun setupInputMac(tag: RuuviTagEntity) {
        inputMacTextView.text = tag.id

        inputMacTextView.setOnLongClickListener {
            val clipboard: ClipboardManager? = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val clip = ClipData.newPlainText("Mac address", tag.id)

            try {
                if (BuildConfig.DEBUG && clipboard == null) {
                    error("Assertion failed")
                }

                clipboard?.setPrimaryClip(clip)

                Toast.makeText(this@TagSettingsActivity, "Mac address copied to clipboard", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Could not copy mac to clipboard")
            }
            false
        }
    }

    private fun setupTagImage(tag: RuuviTagEntity) {
        tagImageView.setImageBitmap(Utils.getBackground(this, tag))

        tagImageCameraButton.setDebouncedOnClickListener { showImageSourceSheet() }

        tagImageSelectButton.setDebouncedOnClickListener {
            tag.defaultBackground = if (tag.defaultBackground == 8) 0 else tag.defaultBackground + 1

            tag.userBackground = null

            tagImageView.setImageDrawable(Utils.getDefaultBackground(tag.defaultBackground, applicationContext))
        }
    }

    private fun calibrateHumidity(tag: RuuviTagEntity) {
        calibrateHumidityButton.setDebouncedOnClickListener {
            val builder = AlertDialog.Builder(ContextThemeWrapper(this@TagSettingsActivity, R.style.AppTheme))

            val content = View.inflate(applicationContext, R.layout.dialog_humidity_calibration, null)

            val container = FrameLayout(applicationContext)

            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            params.leftMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)

            params.rightMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin)

            content.layoutParams = params

            (content.findViewById<View>(R.id.info) as TextView).movementMethod = LinkMovementMethod.getInstance()

            (content.findViewById<View>(R.id.calibration) as TextView).text = this.getString(R.string.calibration_hint, Math.round(tag.humidity))

            val calibration = HumidityCalibration.get(tag)

            builder.setPositiveButton("Calibrate") { _, _ ->

                var latestTag = tag.id?.let { it1 -> viewModel.getTagById(it1) }

                HumidityCalibration.calibrate(latestTag)

                if (calibration != null) latestTag?.humidity = latestTag?.humidity?.minus(calibration.humidityOffset)

                latestTag = HumidityCalibration.apply(latestTag)

                viewModel.updateTag(latestTag)

                // so the ui will show calibrated humidity if the user presses the calibration button again

                tag.humidity = latestTag.humidity

                Toast.makeText(this@TagSettingsActivity, "Calibration done!", Toast.LENGTH_SHORT).show()
            }
            if (calibration != null) {
                builder.setNegativeButton("Clear calibration") { _, _ ->
                    HumidityCalibration.clear(tag)

                    // so the ui will show the new uncalibrated value
                    tag.humidity = tag.humidity - calibration.humidityOffset

                    // revert calibration for the latest tag to not mess with calibration if it is done before the tag has updated
                    val latestTag = tag.id?.let { it1 -> viewModel.getTagById(it1) }

                    latestTag?.humidity = latestTag?.humidity?.minus(calibration.humidityOffset)

                    latestTag?.update()
                }
                timestamp.text = this.getString(R.string.calibrated, calibration.timestamp)
            }

            builder.setNeutralButton("Cancel", null)

            container.addView(content)

            builder.setView(container)

            builder.create().show()
        }
    }

    private fun setupAlarmItems() {
        with(viewModel.alarmItems) {
            add(AlarmItem(getString(R.string.temperature), Alarm.TEMPERATURE, false, -40, 85))
            add(AlarmItem(getString(R.string.humidity), Alarm.HUMIDITY, false, 0, 100))
            add(AlarmItem(getString(R.string.pressure), Alarm.PERSSURE, false, 300, 1100))
            add(AlarmItem(getString(R.string.rssi), Alarm.RSSI, false, -105, 0))
            add(AlarmItem(getString(R.string.movement), Alarm.MOVEMENT, false, 0, 0))
        }

        for (alarm in viewModel.tagAlarms) {
            val item = viewModel.alarmItems[alarm.type]
            item.high = alarm.high
            item.low = alarm.low
            item.isChecked = alarm.enabled
            item.alarm = alarm
            item.normalizeValues()
        }

        for (i in viewModel.alarmItems.indices) {
            val item = viewModel.alarmItems[i]

            item.view = layoutInflater.inflate(R.layout.view_alarm, alertsContainerLayout, false)

            item.view?.id = View.generateViewId()

            val checkBox = item.view?.findViewById<CheckBox>(R.id.alertCheckbox)

            checkBox?.tag = i

            checkBox?.setOnCheckedChangeListener(alarmCheckboxListener)

            item.createView()

            alertsContainerLayout.addView(item.view)
        }
    }

    private fun saveOrUpdateAlarmItems() {
        for (alarmItem in viewModel.alarmItems) {
            if (alarmItem.isChecked || alarmItem.low != alarmItem.min || alarmItem.high != alarmItem.max) {
                if (alarmItem.alarm == null) {
                    alarmItem.alarm = Alarm(alarmItem.low, alarmItem.high, alarmItem.type, viewModel.tagId)
                    alarmItem.alarm?.enabled = alarmItem.isChecked
                    alarmItem.alarm?.save()
                } else {
                    alarmItem.alarm?.enabled = alarmItem.isChecked
                    alarmItem.alarm?.low = alarmItem.low
                    alarmItem.alarm?.high = alarmItem.high
                    alarmItem.alarm?.update()
                }
            } else if (alarmItem.alarm != null) {
                alarmItem.alarm?.enabled = false
                alarmItem.alarm?.update()
            }
            if (!alarmItem.isChecked) {
                AlarmCheckInteractor.dismissNotification(if (alarmItem.alarm != null) alarmItem.alarm?.id else -1, this)
            }
        }
    }

    private fun updateReadings(tag: RuuviTagEntity) {
        if (tag.dataFormat == 3 || tag.dataFormat == 5) {
            rawValuesLayout.isVisible = true
            inputVoltageTextView.text = this.getString(R.string.voltage_format, tag.voltage.toString())
            xInputTextView.text = tag.accelX.toString()
            yInputTextView.text = tag.accelY.toString()
            zInputTextView.text = tag.accelZ.toString()
        } else {
            rawValuesLayout.isVisible = false
        }
    }

    private fun delete() {
        val builder = AlertDialog.Builder(this)

        builder.setTitle(this.getString(R.string.tag_delete_title))

        builder.setMessage(this.getString(R.string.tag_delete_message))

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            for (alarm: AlarmItem in viewModel.alarmItems) {
                alarm.alarm?.let {
                    AlarmCheckInteractor.dismissNotification(it.id, this)
                }
            }
            viewModel.tag?.let { viewModel.deleteTag(it) }
            finish()
        }

        builder.setNegativeButton(android.R.string.cancel, null)

        builder.show()
    }

    private fun showImageSourceSheet() {
        val sheetDialog = BottomSheetDialog(this)

        val listView = ListView(this)

        val menu = arrayOf(
            resources.getString(R.string.camera),
            resources.getString(R.string.gallery)
        )

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, menu)

        listView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            when (position) {
                0 -> dispatchTakePictureIntent()
                1 -> imageFromGallery
            }
            sheetDialog.dismiss()
        }

        sheetDialog.setContentView(listView)

        sheetDialog.show()
    }

    private var currentPhotoPath: String? = null

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val imageFileName = "background_" + viewModel.tagId
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        currentPhotoPath = image.absolutePath
        return image
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the File where the photo should go
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                try {
                    photoFile.createNewFile()
                } catch (ioEx: IOException) {
                    Toast.makeText(this, "Could not start camera", Toast.LENGTH_SHORT).show()
                    return
                }
                viewModel.file = FileProvider.getUriForFile(
                    this,
                    "com.ruuvi.station.fileprovider",
                    photoFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, viewModel.file)
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
            }
        }
    }

    private val imageFromGallery: Unit
        get() {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_GALLERY_PHOTO)
        }

    private fun isImage(uri: Uri?): Boolean {
        val mime = uri?.let { getMimeType(it) }
        return mime == "jpeg" || mime == "jpg" || mime == "png"
    }

    private fun getMimeType(uri: Uri): String? {
        val contentResolver = applicationContext.contentResolver
        val mime = MimeTypeMap.getSingleton()
        return mime.getExtensionFromMimeType(contentResolver.getType(uri))
    }

    private fun getCameraPhotoOrientation(file: Uri?): Int {
        var rotate = 0
        file?.let {
            try {
                applicationContext.contentResolver.openInputStream(file).use { inputStream ->
                    val exif = ExifInterface(inputStream!!)

                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not get orientation of image")
            }
        }

        return rotate
    }

    private fun resize(uri: Uri?, rotation: Int) {
        try {
            val displayMetrics = DisplayMetrics()

            windowManager.defaultDisplay.getMetrics(displayMetrics)

            val targetHeight = 1440

            val targetWidth = 960

            var bitmap = MediaStore.Images.Media.getBitmap(applicationContext.contentResolver, uri)

            bitmap = rotate(bitmap, rotation.toFloat())

            var out: Bitmap

            out = if ((targetHeight.toFloat() / bitmap.height.toFloat() * bitmap.width).toInt() > targetWidth) {
                Bitmap.createScaledBitmap(bitmap, (targetHeight.toFloat() / bitmap.height.toFloat() * bitmap.width).toInt(), targetHeight, false)
            } else {
                Bitmap.createScaledBitmap(bitmap, targetWidth, (targetWidth.toFloat() / bitmap.width.toFloat() * bitmap.height).toInt(), false)
            }

            var x = out.width / 2 - targetWidth / 2

            if (x < 0) x = 0

            out = Bitmap.createBitmap(out, x, 0, targetWidth, targetHeight)

            val file = File(currentPhotoPath)

            val fileOutputStream = FileOutputStream(file)

            out.compress(Bitmap.CompressFormat.JPEG, 60, fileOutputStream)

            fileOutputStream.flush()

            fileOutputStream.close()

            bitmap.recycle()

            out.recycle()
        } catch (e: Exception) {
            Timber.e(e, "Could not resize background image")
        }
    }

    inner class AlarmItem(var name: String, var type: Int, var isChecked: Boolean, var min: Int, var max: Int) {
        private var subtitle: String? = null
        var low: Int
        var high: Int
        var view: View? = null
        var alarm: Alarm? = null

        fun createView() {
            view?.let { view ->
                val seekBar: CrystalRangeSeekbar = view.findViewById(R.id.alertSeekBar)

                seekBar.setMinValue(min.toFloat())

                seekBar.setMaxValue(max.toFloat())

                seekBar.setMinStartValue(low.toFloat())

                seekBar.setMaxStartValue(high.toFloat())

                seekBar.apply()

                seekBar.setOnRangeSeekbarChangeListener { minValue: Number, maxValue: Number ->
                    low = minValue.toInt()
                    high = maxValue.toInt()
                    updateView()
                }

                if (min == 0 && max == 0) {
                    seekBar.isVisible = false
                    view.findViewById<View>(R.id.alertMinValueTextView).visibility = View.GONE
                    view.findViewById<View>(R.id.alertMaxValueTextView).visibility = View.GONE
                }
            }

            updateView()
        }

        fun updateView() {
            view?.let {view ->
                val seekBar: CrystalRangeSeekbar = view.findViewById(R.id.alertSeekBar)

                var setSeekbarColor = R.color.inactive

                if (isChecked) {
                    setSeekbarColor = R.color.main
                    subtitle = getString(R.string.alert_substring_movement)
                    subtitle = when (type) {
                        Alarm.MOVEMENT -> getString(R.string.alert_substring_movement)
                        Alarm.TEMPERATURE -> when (viewModel.tempUnit) {
                            "K" -> {
                                String.format(getString(R.string.alert_subtitle_on),
                                    Utils.celsiusToKelvin(low.toDouble()).toInt(),
                                    Utils.celsiusToKelvin(high.toDouble()).toInt())
                            }
                            "F" -> {
                                String.format(getString(R.string.alert_subtitle_on),
                                    Utils.celciusToFahrenheit(low.toDouble()).toInt(),
                                    Utils.celciusToFahrenheit(high.toDouble()).toInt())
                            }
                            else -> {
                                String.format(getString(R.string.alert_subtitle_on), low, high)
                            }
                        }
                        else -> String.format(getString(R.string.alert_subtitle_on), low, high)
                    }
                } else {
                    subtitle = getString(R.string.alert_subtitle_off)
                }

                seekBar.setRightThumbColor(ContextCompat.getColor(this@TagSettingsActivity, setSeekbarColor))

                seekBar.setRightThumbHighlightColor(ContextCompat.getColor(this@TagSettingsActivity, setSeekbarColor))

                seekBar.setLeftThumbColor(ContextCompat.getColor(this@TagSettingsActivity, setSeekbarColor))

                seekBar.setLeftThumbHighlightColor(ContextCompat.getColor(this@TagSettingsActivity, setSeekbarColor))

                seekBar.setBarHighlightColor(ContextCompat.getColor(this@TagSettingsActivity, setSeekbarColor))

                seekBar.isEnabled = isChecked

                (view.findViewById<View>(R.id.alertCheckbox) as CheckBox).isChecked = isChecked

                (view.findViewById<View>(R.id.alertTitleTextView) as TextView).text = name

                (view.findViewById<View>(R.id.alertSubtitleTextView) as TextView).text = subtitle

                if (type == Alarm.TEMPERATURE) {
                    when (viewModel.tempUnit) {
                        "K" -> {
                            (view.findViewById<View>(R.id.alertMinValueTextView) as TextView).text =
                                Utils.celsiusToKelvin(low.toDouble()).toInt().toString()
                            (view.findViewById<View>(R.id.alertMaxValueTextView) as TextView).text =
                                Utils.celsiusToKelvin(high.toDouble()).toInt().toString()
                        }
                        "F" -> {
                            (view.findViewById<View>(R.id.alertMinValueTextView) as TextView).text =
                                Utils.celciusToFahrenheit(low.toDouble()).toInt().toString()
                            (view.findViewById<View>(R.id.alertMaxValueTextView) as TextView).text =
                                Utils.celciusToFahrenheit(high.toDouble()).toInt().toString()
                        }
                        else -> {
                            (view.findViewById<View>(R.id.alertMinValueTextView) as TextView).text = low.toString()
                            (view.findViewById<View>(R.id.alertMaxValueTextView) as TextView).text = high.toString()
                        }
                    }
                } else {
                    (view.findViewById<View>(R.id.alertMinValueTextView) as TextView).text = low.toString()
                    (view.findViewById<View>(R.id.alertMaxValueTextView) as TextView).text = high.toString()
                }
            }
        }

        fun normalizeValues() {
            if (low < min) low = min
            if (low >= max) low = max - 1
            if (high > max) high = max
            if (high < min) high = min + 1
            if (low > high) {
                low = high.also { high = low }
            }
        }

        init {
            low = min
            high = max
        }
    }

    companion object {
        const val TAG_ID = "TAG_ID"

        const val REQUEST_TAKE_PHOTO = 1

        const val REQUEST_GALLERY_PHOTO = 2

        fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}