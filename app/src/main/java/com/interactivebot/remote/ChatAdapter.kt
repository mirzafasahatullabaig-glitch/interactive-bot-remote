package com.interactivebot.remote

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A chat line: [mine] true = typed by the user, false = from the bot. */
data class Msg(val text: String, val mine: Boolean)

/** Minimal chat list: user lines right-aligned, bot lines left-aligned.
 *  Built in code (no item layout XML) to keep the app to as few files as needed. */
class ChatAdapter(private val items: MutableList<Msg>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            textSize = 16f
            setPadding(28, 16, 28, 16)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.tv.text = m.text
        holder.tv.gravity = if (m.mine) Gravity.END else Gravity.START
        holder.tv.setTextColor(if (m.mine) Color.parseColor("#2962FF") else Color.parseColor("#E0E0E0"))
        holder.tv.setTypeface(null, if (m.mine) Typeface.NORMAL else Typeface.NORMAL)
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
