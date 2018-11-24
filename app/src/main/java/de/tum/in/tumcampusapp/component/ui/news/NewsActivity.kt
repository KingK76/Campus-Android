package de.tum.`in`.tumcampusapp.component.ui.news

import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.tum.`in`.tumcampusapp.R
import de.tum.`in`.tumcampusapp.component.other.generic.activity.ActivityForDownloadingExternal
import de.tum.`in`.tumcampusapp.component.other.generic.adapter.EqualSpacingItemDecoration
import de.tum.`in`.tumcampusapp.component.ui.news.repository.NewsLocalRepository
import de.tum.`in`.tumcampusapp.utils.Const
import de.tum.`in`.tumcampusapp.utils.NetUtils
import de.tum.`in`.tumcampusapp.utils.Utils
import javax.inject.Inject

/**
 * Activity to show News (message, image, date)
 */
class NewsActivity : ActivityForDownloadingExternal(
        Const.NEWS,
        R.layout.activity_news
), DialogInterface.OnMultiChoiceClickListener {

    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.activity_news_list_view) }
    private var state = -1

    @Inject
    lateinit var newsLocalRepository: NewsLocalRepository

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(this)

        requestDownload(false)
        showLoadingEnded()
        initRecyclerView()
    }

    private fun initRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        val spacing = Math.round(resources.getDimension(R.dimen.material_card_view_padding))
        recyclerView.addItemDecoration(EqualSpacingItemDecoration(spacing))
    }

    override fun onStart() {
        super.onStart()

        // Gets all news from database
        val news = newsLocalRepository.getAll()
        if (news.isEmpty()) {
            if (NetUtils.isConnected(this)) {
                showErrorLayout()
            } else {
                showNoInternetLayout()
            }
            return
        }

        val adapter = NewsAdapter(this, news)
        recyclerView.adapter = adapter

        // Restore previous state (including selected item index and scroll position)
        if (state == -1) {
            recyclerView.scrollToPosition(newsLocalRepository.getTodayIndex())
        } else {
            recyclerView.scrollToPosition(state)
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int, isChecked: Boolean) {
        val newsSources = newsLocalRepository.getNewsSources()

        if (which < newsSources.size) {
            val key = "news_source_" + newsSources[which].id
            Utils.setSetting(this, key, isChecked)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            state = layoutManager.findFirstVisibleItemPosition()

            requestDownload(false)
        }
    }

    /**
     * Save ListView state
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        state = layoutManager.findFirstVisibleItemPosition()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_activity_news, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_disable_sources) {
            // Populate the settingsPrefix dialog from the NewsController sources
            val (items, checkedItems) = newsLocalRepository.getNewsSources()
                    .map { (id, title) ->
                        title to Utils.getSettingBool(this, "news_source_$id", true) }
                    .unzip()

            val dialog = AlertDialog.Builder(this)
                    .setMultiChoiceItems(items.toTypedArray(), checkedItems.toBooleanArray(), this)
                    .create()

            if (dialog.window != null) {
                dialog.window!!.setBackgroundDrawableResource(R.drawable.rounded_corners_background)
            }

            dialog.show()

            return true
        }
        return super.onOptionsItemSelected(item)
    }

}