package com.fujitsu.frontech.palmsecure_sample.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fujitsu.frontech.palmsecure_gui_sample.R

class UserListDialog(context: Context, private val userIds: List<String>, private val onUserSelected: (String) -> Unit) : Dialog(context) {

    private var selectedUserId: String? = null
    private lateinit var adapter: UserAdapter
    private lateinit var selectButton: Button
    private lateinit var emptyTextView: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_list_dialog)

        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)

        recyclerView = findViewById(R.id.userRecyclerView)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        selectButton = findViewById(R.id.selectButton)
        val closeButton = findViewById<Button>(R.id.closeButton)
        emptyTextView = findViewById(R.id.emptyTextView)

        selectButton.isEnabled = false

        adapter = UserAdapter(userIds) { userId ->
            selectedUserId = userId
            selectButton.isEnabled = userId.isNotEmpty()
            updateEmptyState()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // Add Dividers (Suggested by common list patterns)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        // IME Inset Handling (Suggested in Views.md L304, L321)
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeInsets.bottom)
            insets
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        selectButton.setOnClickListener {
            selectedUserId?.let {
                if (it.isNotEmpty()) {
                    onUserSelected(it)
                    dismiss()
                }
            }
        }

        closeButton.setOnClickListener { dismiss() }
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            emptyTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private class UserAdapter(private val fullList: List<String>, private val onSelected: (String) -> Unit) :
        RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        private var filteredList = fullList
        private var selectedUserId: String? = null

        fun filter(query: String) {
            val newList = if (query.isEmpty()) {
                fullList
            } else {
                fullList.filter { it.contains(query, ignoreCase = true) }
            }

            // DiffUtil for smooth updates (Advised for performance/UX)
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = filteredList.size
                override fun getNewListSize(): Int = newList.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = filteredList[oldPos] == newList[newPos]
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = filteredList[oldPos] == newList[newPos]
            })

            filteredList = newList
            if (!filteredList.contains(selectedUserId)) {
                selectedUserId = null
                onSelected("")
            } else {
                // Ensure dialog state remains correct even if list changes
                onSelected(selectedUserId ?: "")
            }
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.user_item, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val userId = filteredList[position]
            holder.userIdText.text = userId
            holder.radioButton.isChecked = userId == selectedUserId

            holder.itemView.setOnClickListener {
                val oldSelectedId = selectedUserId
                selectedUserId = userId
                
                if (oldSelectedId != null) {
                    val oldPos = filteredList.indexOf(oldSelectedId)
                    if (oldPos != -1) notifyItemChanged(oldPos)
                }
                notifyItemChanged(holder.bindingAdapterPosition)
                
                onSelected(userId)
            }
        }

        override fun getItemCount(): Int = filteredList.size

        class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val userIdText: TextView = view.findViewById(R.id.userIdTextView)
            val radioButton: RadioButton = view.findViewById(R.id.userRadioButton)
        }
    }
}