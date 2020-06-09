package com.ruuvi.station.tagdetails.ui

import androidx.lifecycle.Observer
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.text.SpannableString
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.flexsentlabs.extensions.sharedViewModel
import com.flexsentlabs.extensions.viewModel
import com.ruuvi.station.R
import com.ruuvi.station.database.tables.RuuviTagEntity
import com.ruuvi.station.graph.GraphView
import com.ruuvi.station.tagdetails.di.TagViewModelArgs
import com.ruuvi.station.util.Utils
import kotlinx.android.synthetic.main.view_tag_detail.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.support.closestKodein
import timber.log.Timber

class TagFragment : Fragment(), KodeinAware {
    override val kodein: Kodein by closestKodein()

    private val viewModel: TagViewModel by viewModel {
        arguments?.let {
            TagViewModelArgs(it.getString(TAG_ID, ""))
        }
    }

    private val activityViewModel: TagDetailsViewModel by sharedViewModel()

    private val graphView = GraphView()

    init {
        Timber.d("new TagFragment")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.view_tag_detail, container, false)
        val showGraph = activityViewModel.getCurrentShowGraph()
        setupViewVisibility(view, showGraph)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeShowGraph()
        observeTagEntry()
        observeTagReadings()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopShowGraph()
    }
    override fun onResume() {
        super.onResume()
        if (activityViewModel.getCurrentShowGraph()) {
            viewModel.startShowGraph()
        } else {
            viewModel.stopShowGraph()
        }
    }

    private fun observeTagEntry() {
        lifecycleScope.launch {
            viewModel.tagEntry.consumeEach {
                updateTagData(it)
            }
        }
    }

    private fun updateTagData(tag: RuuviTagEntity) {
        Timber.d("updateTagData for ${tag.id}")
        var temperature = viewModel.getTemperatureString(requireContext(), tag)
        val offset = if (temperature.endsWith("K")) 1 else 2
        val unit = temperature.substring(temperature.length - offset, temperature.length)
        temperature = temperature.substring(0, temperature.length - offset)
        tag_temp.text = temperature

        tag_humidity.text = viewModel.getHumidityString(requireContext(), tag)
        tag_pressure.text = getString(R.string.pressure_reading, tag.pressure / 100.0)
        tag_signal.text = getString(R.string.signal_reading, tag.rssi)
        tag_updated.text = getString(R.string.updated, Utils.strDescribingTimeSince(tag.updateAt))

        val unitSpan = SpannableString(unit)
        unitSpan.setSpan(SuperscriptSpan(), 0, unit.length, 0)
        tag_temp_unit.text = unitSpan
    }

    private fun observeShowGraph() {
        activityViewModel.observeShowGraph().observe(viewLifecycleOwner, Observer { showGraph ->
            view?.let {
                setupViewVisibility(it, showGraph)
                if (showGraph == true && lifecycle.currentState == Lifecycle.State.RESUMED) {
                    viewModel.startShowGraph()
                } else {
                    viewModel.stopShowGraph()
                }
            }
        })
    }

    private fun setupViewVisibility(view: View, showGraph: Boolean) {
        val graph = view.findViewById<View>(R.id.tag_graphs)
        val container = view.findViewById<View>(R.id.tag_container)

        graph.isInvisible = !showGraph
        container.isInvisible = showGraph
    }

    private fun observeTagReadings() {
        lifecycleScope.launch {
            viewModel.tagReadings.consumeEach { readings ->
                view?.let { view ->
                    graphView.drawChart(readings, view, requireContext())
                }
            }
        }
    }

    companion object {
        const val TAG_ID = "TAG_ID"

        fun newInstance(tagEntity: RuuviTagEntity): TagFragment {
            val tagFragment = TagFragment()
            val args = Bundle()
            args.putString(TAG_ID, tagEntity.id ?: "")
            tagFragment.arguments = args
            return tagFragment
        }
    }
}