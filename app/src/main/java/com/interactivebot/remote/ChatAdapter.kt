package com.interactivebot.remote

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** A chat line: [mine] true = typed by the user, false = from the bot. */
data class Msg(val text: String, val mine: Boolean)

/** Chat bubbles: user bubbles right/accent, bot bubbles left/panel, each at
 *  most ~80% of the width. Built in code (no item layout XML) to keep the app
 *  to as few files as needed; styling mirrors the Pi kiosk. */
class ChatAdapter(private val items: MutableList<Msg>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    /** A bubble TextView inside a full-width frame that aligns it left/right. */
    class VH(val frame: FrameLayout, val tv: TextView) : RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp = ctx.resources.displayMetrics.density
        val tv = TextView(ctx).apply {
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, R.color.txt))
            setLineSpacing(0f, 1.1f)
            val padH = (14 * dp).toInt(); val padV = (10 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            // cap bubble width so long replies wrap instead of filling the row
            maxWidth = (parent.width * 0.8f).toInt().coerceAtLeast((240 * dp).toInt())
        }
        val frame = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            val mv = (4 * dp).toInt(); val mh = (8 * dp).toInt()
            setPadding(mh, mv, mh, mv)
            addView(tv)
        }
        return VH(frame, tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.tv.text = m.text
        holder.tv.setBackgroundResource(if (m.mine) R.drawable.bubble_me else R.drawable.bubble_bot)
        (holder.tv.layoutParams as FrameLayout.LayoutParams).gravity =
            if (m.mine) Gravity.END else Gravity.START
    }

    override fun getItemCount() = items.size

    /** Append a new line (or, for a streamed bot reply, extend the last bot line). */
    fun add(m: Msg) {
        items.add(m)
        notifyItemInserted(items.size - 1)
    }

    /** Append streamed text onto the last bot line, or start one if needed. */
    fun appendToLastBot(chunk: String) {
        val last = items.lastOrNull()
        if (last != null && !last.mine) {
            items[items.size - 1] = last.copy(text = last.text + chunk)
            notifyItemChanged(items.size - 1)
        } else {
            add(Msg(chunk, false))
        }
    }
}
