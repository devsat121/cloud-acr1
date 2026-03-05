package com.cloudacr.app.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudacr.app.R
import com.cloudacr.app.databinding.ActivityMainBinding
import com.cloudacr.app.service.CallRecordingService
import com.cloudacr.app.service.HelperCommandReceiver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: RecordingAdapter

    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isRecording = intent.getBooleanExtra(CallRecordingService.EXTRA_IS_RECORDING, false)
            viewModel.setIsRecording(isRecording)
            updateRecordingBadge(isRecording)
        }
    }

    private val helperStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connected = intent.getBooleanExtra("connected", false)
            updateHelperBadge(connected)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Some permissions denied — recording may not work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupFab()
        observeViewModel()
        requestPermissions()
    }

    private fun setupRecyclerView() {
        adapter = RecordingAdapter(
            onItemClick = { recording ->
                val sel = viewModel.selectedIds.value
                if (sel.isNotEmpty()) {
                    viewModel.toggleSelection(recording.id)
                } else {
                    startActivity(
                        Intent(this, RecordingDetailActivity::class.java)
                            .putExtra("recording_id", recording.id)
                    )
                }
            },
            onItemLongClick = { recording ->
                viewModel.toggleSelection(recording.id)
                true
            },
            onStarClick = { recording ->
                viewModel.toggleStarred(recording)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabManualRecord.setOnClickListener {
            if (viewModel.isRecording.value == true) {
                CallRecordingService.stopRecording(this)
            } else {
                showManualRecordDialog()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.recordings.collectLatest { list ->
                adapter.submitList(list)
                binding.emptyStateView.visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.stats.collectLatest { (count, size, duration) ->
                val sizeStr = when {
                    size > 1_048_576 -> "%.1f MB".format(size / 1_048_576.0)
                    size > 1024 -> "%.1f KB".format(size / 1024.0)
                    else -> "$size B"
                }
                val mins = duration / 1000 / 60
                binding.statsText.text = "$count recordings · $sizeStr · ${mins}m total"
            }
        }

        viewModel.isRecording.observe(this) { isRec ->
            updateRecordingBadge(isRec)
        }

        lifecycleScope.launch {
            viewModel.selectedIds.collectLatest { sel ->
                updateSelectionMenu(sel.size)
            }
        }
    }

    private fun updateRecordingBadge(isRecording: Boolean) {
        binding.recordingStatusChip.visibility = if (isRecording) View.VISIBLE else View.GONE
        binding.fabManualRecord.setImageResource(
            if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic
        )
    }

    private fun updateHelperBadge(connected: Boolean) {
        binding.helperChip.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun updateSelectionMenu(count: Int) {
        invalidateOptionsMenu()
        supportActionBar?.title = if (count > 0) "$count selected" else getString(R.string.app_name)
    }

    private fun showManualRecordDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Phone number (optional)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(this)
            .setTitle("Manual Recording")
            .setMessage("Start recording now?")
            .setView(input)
            .setPositiveButton("Record") { _, _ ->
                val num = input.text.toString().trim()
                CallRecordingService.startRecording(this, num, com.cloudacr.app.data.Recording.CallType.UNKNOWN)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String) = false
            override fun onQueryTextChange(q: String): Boolean {
                viewModel.setSearchQuery(q)
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasSel = viewModel.selectedIds.value.isNotEmpty()
        menu.findItem(R.id.action_delete_selected)?.isVisible = hasSel
        menu.findItem(R.id.action_select_all)?.isVisible = hasSel
        menu.findItem(R.id.action_clear_selection)?.isVisible = hasSel
        menu.findItem(R.id.action_filter_starred)?.isVisible = !hasSel
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_filter_starred -> {
                item.isChecked = !item.isChecked
                viewModel.setShowStarredOnly(item.isChecked)
                true
            }
            R.id.action_delete_selected -> {
                confirmDeleteSelected()
                true
            }
            R.id.action_select_all -> {
                viewModel.selectAll()
                true
            }
            R.id.action_clear_selection -> {
                viewModel.clearSelection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDeleteSelected() {
        val count = viewModel.selectedIds.value.size
        AlertDialog.Builder(this)
            .setTitle("Delete $count recording${if (count > 1) "s" else ""}?")
            .setMessage("This will permanently delete the audio files.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteSelected() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            recordingStateReceiver,
            IntentFilter(CallRecordingService.ACTION_RECORDING_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )
        registerReceiver(
            helperStatusReceiver,
            IntentFilter("com.cloudacr.HELPER_STATUS_CHANGED"),
            RECEIVER_NOT_EXPORTED
        )
        updateHelperBadge(HelperCommandReceiver.isHelperConnected)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(recordingStateReceiver)
        unregisterReceiver(helperStatusReceiver)
    }
}
