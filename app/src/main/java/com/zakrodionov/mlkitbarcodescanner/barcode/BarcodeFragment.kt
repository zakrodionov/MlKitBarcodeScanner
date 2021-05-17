package com.zakrodionov.mlkitbarcodescanner.barcode

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.internal.Objects
import com.google.android.material.chip.Chip
import com.zakrodionov.mlkitbarcodescanner.R
import com.zakrodionov.mlkitbarcodescanner.barcode.camera.CameraSource
import com.zakrodionov.mlkitbarcodescanner.barcode.camera.CameraSourcePreview
import com.zakrodionov.mlkitbarcodescanner.barcode.camera.GraphicOverlay
import com.zakrodionov.mlkitbarcodescanner.barcode.camera.WorkflowModel
import com.zakrodionov.mlkitbarcodescanner.barcode.utils.Utils
import java.io.IOException

class BarcodeFragment : Fragment(R.layout.fragment_barcode), View.OnClickListener {
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(view) {
            preview = findViewById(R.id.camera_preview)
            graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
                setOnClickListener(this@BarcodeFragment)
                cameraSource = CameraSource(this)
            }

            promptChip = findViewById(R.id.bottom_prompt_chip)
            promptChipAnimator =
                (AnimatorInflater.loadAnimator(
                    requireContext(),
                    R.animator.bottom_prompt_chip_enter
                ) as AnimatorSet).apply {
                    setTarget(promptChip)
                }

            findViewById<View>(R.id.close_button).setOnClickListener(this@BarcodeFragment)
            flashButton = findViewById<View>(R.id.flash_button).apply {
                setOnClickListener(this@BarcodeFragment)
            }
            settingsButton = findViewById<View>(R.id.settings_button).apply {
                setOnClickListener(this@BarcodeFragment)
            }
        }

        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        if (!Utils.allPermissionsGranted(requireContext())) {
            Utils.requestRuntimePermissions(requireActivity())
        }

        workflowModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay!!, workflowModel!!))
        workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.close_button -> requireActivity().finish()
            R.id.flash_button -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
        }
    }

    private fun startCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProvider(this).get(WorkflowModel::class.java)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel?.workflowState?.observe(viewLifecycleOwner, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(TAG, "Current workflow state: ${currentWorkflowState!!.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE

            when (workflowState) {
                WorkflowModel.WorkflowState.DETECTING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_point_at_a_barcode)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.CONFIRMING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_move_camera_closer)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.SEARCHING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_searching)
                    stopCameraPreview()
                }
                WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                    promptChip?.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> promptChip?.visibility = View.GONE
            }

            val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        // TODO переделать
        workflowModel?.detectedBarcode?.observe(viewLifecycleOwner, { barcode ->
            Log.d(TAG, "${barcode?.rawValue?.toString() ?: "null"}")
            if (barcode.rawValue?.isNotEmpty() == true) {
                Toast.makeText(requireContext(), barcode.rawValue.toString(), Toast.LENGTH_SHORT).show()

                Handler().postDelayed({ workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING) }, 2000)
            }
        })
    }

    companion object {
        private const val TAG = "BarcodeFragment"
    }
}