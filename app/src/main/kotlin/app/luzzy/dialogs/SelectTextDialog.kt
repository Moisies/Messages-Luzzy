package app.luzzy.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.copyToClipboard
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import app.luzzy.databinding.DialogSelectTextBinding

class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogSelectTextBinding.inflate(activity.layoutInflater).apply {
            dialogSelectTextValue.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ -> { } }
            .setNeutralButton(com.goodwy.commons.R.string.copy) { _, _ -> activity.copyToClipboard(text) }
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }
}
