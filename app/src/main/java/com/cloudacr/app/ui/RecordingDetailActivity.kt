package com.cloudacr.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.cloudacr.app.CloudACRApp
import com.cloudacr.app.R
import com.cloudacr.app.data.Recording
import com.cloudacr.app.databinding.ActivityRecordingDetailBinding
import com.cloudacr.app.utils.StorageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingDetailBinding
    private var recording: Recording? = null
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recordingId = intent.getLongExtra("recording_id", -1)
        if (recordingId == -1L) { finish(); return }

        loadRecording(recordingId)
    }

    private fun loadRecording(id: Long) {
        lifecycleScope.launch {
            val repo = (application as CloudACRApp).repository
            val rec = repo.getById(id) ?: run { finish(); return@launch }
            recording = rec
            displayRecording(rec)
        }
    }

    private fun displayRecording(rec: Recording) {
        supportActionBar?.title = rec.displayName

        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy  h:mm a", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date(rec.timestamp))
        binding.tvPhone.text = rec.phoneNumber.ifBlank { "Unknown" }
        binding.tvDuration.text = rec.formattedDuration
        binding.tvSize.text = rec.formattedSize
        binding.tvCallType.text = when (rec.callType) {
            Recording.CallType.INCOMING -> "↙ Incoming Call"
            Recording.CallType.OUTGOING -> "↗ Outgoing Call"
            Recording.CallType.UNKNOWN -> "↕ Call"
        }
        binding.tvFileName.text = rec.fileName
        binding.etNotes.setText(rec.notes ?: "")
        binding.tvTranscription.text = rec.transcription ?: "No transcription available"

        binding.ivStar.setImageResource(
            if (rec.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        binding.ivStar.setOnClickListener {
            lifecycleScope.launch {
                val repo = (application as CloudACRApp).repository
                repo.setStarred(rec.id, !rec.isStarred)
                recording = rec.copy(isStarred = !rec.isStarred)
                binding.ivStar.setImageResource(
                    if (recording!!.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                )
            }
        }

        setupMediaPlayer(rec.filePath)
        setupSaveNotesButton(rec)
    }

    private fun setupMediaPlayer(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            binding.tvPlayerError.visibility = android.view.View.VISIBLE
            return
        }

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
        }

        binding.seekBar.max = mediaPlayer!!.duration
        binding.tvTotalTime.text = StorageUtils.formatDuration(mediaPlayer!!.duration.toLong())

        binding.btnPlayPause.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                stopProgressUpdate()
            } else {
                mediaPlayer?.start()
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                startProgressUpdate()
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    binding.tvCurrentTime.text = StorageUtils.formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        mediaPlayer?.setOnCompletionListener {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = "0:00"
            stopProgressUpdate()
        }
    }

    private fun startProgressUpdate() {
        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        binding.seekBar.progress = it.currentPosition
                        binding.tvCurrentTime.text = StorageUtils.formatDuration(it.currentPosition.toLong())
                        handler.postDelayed(this, 200)
                    }
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdate() {
        progressRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun setupSaveNotesButton(rec: Recording) {
        binding.btnSaveNotes.setOnClickListener {
            val notes = binding.etNotes.text.toString().trim()
            lifecycleScope.launch {
                val repo = (application as CloudACRApp).repository
                repo.updateNotes(rec.id, notes.ifBlank { null })
                Toast.makeText(this@RecordingDetailActivity, "Notes saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_share -> {
                shareRecording()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareRecording() {
        val rec = recording ?: return
        val file = File(rec.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Call recording - ${rec.displayName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share recording"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete recording?")
            .setMessage("This will permanently delete the audio file.")
            .setPositiveButton("Delete") { _, _ ->
                recording?.let { rec ->
                    lifecycleScope.launch {
                        try { File(rec.filePath).delete() } catch (e: Exception) { }
                        (application as CloudACRApp).repository.delete(rec)
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
