package app.luzzy.activities

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import app.luzzy.R
import app.luzzy.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : SimpleActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    private val binding by viewBinding(ActivityVideoPlayerBinding::inflate)
    private var isInPipMode = false
    private var videoUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupEdgeToEdge(padBottomSystem = listOf(binding.videoContainer))

        videoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"
        binding.toolbar.title = videoTitle

        if (videoUri == null) {
            toast(R.string.video_player_error)
            finish()
            return
        }

        setupVideoPlayer()
        setupControls()
        checkPipSupport()
    }

    private fun setupVideoPlayer() {
        binding.progressBar.visibility = View.VISIBLE

        binding.videoView.apply {
            setVideoURI(videoUri)
            setOnPreparedListener { player ->
                binding.progressBar.visibility = View.GONE
                player.start()
                updateTotalTime()
                startSeekBarUpdate()
                updatePlayPauseButton()
            }

            setOnCompletionListener {
                updatePlayPauseButton()
                stopSeekBarUpdate()
            }

            setOnErrorListener { _, what, extra ->
                binding.progressBar.visibility = View.GONE
                toast(R.string.video_player_error)
                finish()
                true
            }
        }

        binding.videoContainer.setOnClickListener {
            toggleControls()
        }
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.videoView.seekTo(progress)
                    updateCurrentTime()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopSeekBarUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startSeekBarUpdate()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.pipButton.setOnClickListener {
                enterPipMode()
            }
        } else {
            binding.pipButton.visibility = View.GONE
        }
    }

    private fun checkPipSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPipFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            binding.pipButton.visibility = if (hasPipFeature) View.VISIBLE else View.GONE
        } else {
            binding.pipButton.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()

            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (binding.videoView.isPlaying) {
                enterPipMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            binding.appbar.visibility = View.GONE
            binding.videoControls.visibility = View.GONE
        } else {
            binding.appbar.visibility = View.VISIBLE
            binding.videoControls.visibility = View.VISIBLE
        }
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            stopSeekBarUpdate()
        } else {
            binding.videoView.start()
            startSeekBarUpdate()
        }
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        val iconRes = if (binding.videoView.isPlaying) {
            R.drawable.ic_pause_vector
        } else {
            R.drawable.ic_play_vector
        }
        binding.playPauseButton.setImageResource(iconRes)
    }

    private fun toggleControls() {
        if (isInPipMode) return

        val newVisibility = if (binding.videoControls.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.appbar.visibility = newVisibility
        binding.videoControls.visibility = newVisibility
    }

    private fun startSeekBarUpdate() {
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                if (binding.videoView.isPlaying) {
                    updateSeekBar()
                    updateCurrentTime()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(updateSeekBarRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun updateSeekBar() {
        val duration = binding.videoView.duration
        val currentPosition = binding.videoView.currentPosition

        binding.seekBar.max = duration
        binding.seekBar.progress = currentPosition
    }

    private fun updateCurrentTime() {
        val currentPosition = binding.videoView.currentPosition
        binding.currentTime.text = formatTime(currentPosition)
    }

    private fun updateTotalTime() {
        val duration = binding.videoView.duration
        binding.totalTime.text = formatTime(duration)
        binding.seekBar.max = duration
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    override fun onPause() {
        super.onPause()
        if (!isInPipMode) {
            binding.videoView.pause()
            stopSeekBarUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isInPipMode && binding.videoView.duration > 0) {
            updatePlayPauseButton()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekBarUpdate()
        binding.videoView.stopPlayback()
    }
}
