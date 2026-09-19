package com.jb.netshift

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jb.netshift.data.AppDatabase
import java.util.concurrent.Executors

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var clearButton: Button
    private val adapter = NetworkEventAdapter()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.historyRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        clearButton = view.findViewById(R.id.clearHistoryButton)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        clearButton.setOnClickListener {
            clearHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        executor.execute {
            val context = context ?: return@execute
            val db = AppDatabase.getDatabase(context)
            val events = db.networkEventDao().get4GEvents()

            activity?.runOnUiThread {
                if (events.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE
                    adapter.submitList(events)
                }
            }
        }
    }

    private fun clearHistory() {
        executor.execute {
            val context = context ?: return@execute
            val db = AppDatabase.getDatabase(context)
            db.networkEventDao().clearAll()
            activity?.runOnUiThread {
                recyclerView.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
        }
    }
}
