package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.ActivityOptions
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.jobs.SyncJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.inputMethodManager
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.app_bar_navigation.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.louiskirsch.quickdynalist.compat.anko.alert
import com.louiskirsch.quickdynalist.compat.anko.okButton
import com.louiskirsch.quickdynalist.compat.anko.toast
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Spannable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.text.style.ImageSpan
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.louiskirsch.quickdynalist.DynalistApp.Companion.MAIN_UI_FRAGMENT
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.text.ThemedSpan
import com.louiskirsch.quickdynalist.utils.int
import com.louiskirsch.quickdynalist.utils.resolveColorAttribute


class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val dynalist: Dynalist = Dynalist(this)

    private var inboxes: List<ItemLocation>? = null
    private var documents: List<Location>? = null
    private var filters: List<FilterLocation>? = null

    companion object {
        const val EXTRA_ITEM_TEXT = "EXTRA_ITEM_TEXT"
        private const val MAX_SUBMENU_ITEMS = 1000
        private val dynalistLinkFragmentPattern = Regex("""(?:z=([^&]+))?&?(?:q=([^&]+))?""")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        setSupportActionBar(toolbar)
        window.allowEnterTransitionOverlap = true

        drawer_layout?.let { drawer_layout ->
            val toggle = ActionBarDrawerToggle(
                    this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
            drawer_layout.addDrawerListener(toggle)
            drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerStateChanged(newState: Int) {}
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                override fun onDrawerClosed(drawerView: View) {}
                override fun onDrawerOpened(drawerView: View) {
                    inputMethodManager.hideSoftInputFromWindow(drawerView.windowToken, 0)
                }
            })
            toggle.syncState()
        }

        nav_view.setNavigationItemSelectedListener(this)
        itemNotes.setOnTouchListener(OnLinkTouchListener())

        val itemsModel = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        val filtersModel = ViewModelProviders.of(this).get(DynalistItemFilterViewModel::class.java)
        val fragmentModel = ViewModelProviders.of(this).get(ItemListFragmentViewModel::class.java)
        val documentModel = ViewModelProviders.of(this).get(DynalistDocumentViewModel::class.java)

        itemsModel.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> { newInboxes ->
            val entries = newInboxes.map { ItemLocation(it) }
            this.inboxes = entries
            nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu?.run {
                fillMenuWithItems(entries, R.id.group_bookmarks_list, 0)
                if (entries.size <= 1) {
                    add(Menu.NONE, R.id.menu_item_bookmarks_hint, 1, R.string.add_inbox).apply {
                        isCheckable = false
                    }
                }
            }
        })
        documentModel.folderedDocumentsLiveData.observe(this, Observer<List<Location>> { docs ->
            this.documents = docs
            nav_view.menu.findItem(R.id.submenu_documents_list).subMenu?.run {
                fillMenuWithItems(docs, R.id.group_documents_list, 1)
            }
        })
        filtersModel.filtersLiveData.observe(this, Observer { filters ->
            val entries = filters.map { FilterLocation(it, this) }
            this.filters = entries
            nav_view.menu.findItem(R.id.submenu_filters_list).subMenu?.run {
                fillMenuWithItems(entries, R.id.group_filters_list, 2)
            }
        })
        fragmentModel.selectedLocation.observe(this, Observer { item ->
            inboxes?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu
                updateCheckedBookmark(menu!!, item, this)
            }
            documents?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_documents_list).subMenu
                updateCheckedBookmark(menu!!, item, this)
            }
            filters?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_filters_list).subMenu
                updateCheckedBookmark(menu!!, item, this)
            }
        })

        if (savedInstanceState == null) {
            createInitialFragment()?.let {
                supportFragmentManager.beginTransaction()
                        .add(R.id.fragment_container, it, MAIN_UI_FRAGMENT)
                        .commit()
            }

            if (dynalist.isAuthenticated && !DialogSetupFragment.wasShownBefore(this)) {
                Intent(this, WizardActivity::class.java).apply {
                    putExtra(WizardActivity.EXTRA_DIALOG_SETUP_ONLY, true)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(this)
                }
            }
        }
    }

    private fun createInitialFragment(): Fragment? {
        val itemText = intent.getCharSequenceExtra(EXTRA_ITEM_TEXT) ?: ""
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val fileId = intent.data!!.lastPathSegment!!
            val match = intent.data!!.fragment?.let { dynalistLinkFragmentPattern.find(it) }
            val itemId = match?.groups?.get(1)?.value ?: "root"
            match?.groups?.let {
                if (it[2] != null) {
                    toast(R.string.error_search_link_not_supported)
                }
            }
            val item = DynalistItem.byServerId(fileId, itemId) ?: dynalist.inbox.apply {
                toast(R.string.error_invalid_url)
            }
            return item?.let { ItemListFragment.newInstance(it, itemText) }
        }
        if (intent.hasExtra(DynalistApp.EXTRA_DISPLAY_FILTER) ||
                intent.hasExtra(DynalistApp.EXTRA_DISPLAY_FILTER_ID)) {
            val filter = intent.extras?.let { dynalist.resolveFilterInBundle(it) }
            if (filter != null)
                return FilteredItemListFragment.newInstance(filter)
        }
        val parent = intent.extras?.let { dynalist.resolveItemInBundle(it) } ?: dynalist.inbox
        val scrollTo = intent.extras?.getLong(DynalistApp.EXTRA_SCROLL_TO_ITEM_ID)?.let { id ->
            if(id != 0L) DynalistItem.box.get(id) else null
        }
        return parent?.let { ItemListFragment.newInstance(it, itemText, scrollTo) }
    }

    private fun SubMenu.fillMenuWithItems(items: List<Location>,
                                          groupId: Int, menuIndex: Int) {
        clear()
        val visibilities = BooleanArray(items.size)
        items.forEachIndexed { i, item ->
            val itemId = i + menuIndex * MAX_SUBMENU_ITEMS
            val title = item.shortenedName
            if (title is Spannable)
                ThemedSpan.applyAll(this@NavigationActivity, title)
            add(groupId, itemId, i, title).apply {
                isCheckable = item !is FolderLocation
                isVisible = item.depth == 0
                if (item is FolderLocation) {
                    setOnMenuItemClickListener {
                        val isOpen = toggleFolderState(title as Spannable)
                        nav_view.post {
                            for (idx in i + 1 until items.size) {
                                val depth = items[idx].depth
                                if (depth <= item.depth)
                                    break
                                val menuItem = getItem(idx)
                                if (isOpen) {
                                    menuItem.isVisible = (depth == item.depth + 1) ||
                                            visibilities[idx]
                                } else {
                                    visibilities[idx] = menuItem.isVisible
                                    menuItem.isVisible = false
                                }
                            }
                        }
                        true
                    }
                }
            }
        }
        ViewModelProviders.of(this@NavigationActivity)
                .get(ItemListFragmentViewModel::class.java).let { fragmentModel ->
                    fragmentModel.selectedLocation.value?.let {
                        updateCheckedBookmark(this, it, items)
                    }
                }
    }

    private fun toggleFolderState(folder: Spannable): Boolean {
        val folderSpan = folder.getSpans(0, 1, ImageSpan::class.java)[0]
        val wasOpen = folderSpan.drawable.level == 1
        val isOpen = !wasOpen
        folderSpan.drawable.level = isOpen.int
        return isOpen
    }

    private fun updateCheckedBookmark(menu: Menu, item: Location,
                                      itemList: List<Location>) {
        val itemIndex = itemList.indexOf(item)
        if (itemIndex >= 0)
            nav_view.setCheckedItem(menu.getItem(itemIndex))
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    override fun onResume() {
        super.onResume()
        if (!dynalist.isAuthenticated) {
            dynalist.authenticate()
        }
        if (supportFragmentManager.findFragmentByTag(MAIN_UI_FRAGMENT) == null) {
            createInitialFragment()?.let {
                supportFragmentManager.beginTransaction()
                        .add(R.id.fragment_container, it, MAIN_UI_FRAGMENT)
                        .commit()
            }
        }
    }

    override fun onBackPressed() {
        if (drawer_layout?.isDrawerOpen(GravityCompat.START) == true) {
            drawer_layout?.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.navigation, menu)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.groupId) {
            R.id.group_bookmarks_list ->
                openDynalistItem(inboxes!![item.itemId].item)
            R.id.group_documents_list -> {
                val location = documents!![item.itemId % MAX_SUBMENU_ITEMS]
                if (location is ItemLocation)
                    openDynalistItem(location.item)
            }
            R.id.group_filters_list ->
                openDynalistItemFilter(filters!![item.itemId % MAX_SUBMENU_ITEMS].filter)
        }
        when (item.itemId) {
            R.id.menu_item_bookmarks_hint -> alert {
                messageResource = R.string.bookmark_hint
                okButton { dynalist.sync() }
                show()
            }
            R.id.send_bug_report -> dynalist.sendBugReport()
            R.id.open_settings -> openSettings()
            R.id.share_quickdynalist -> shareQuickDynalist()
            R.id.rate_quickdynalist -> rateQuickDynalist()
            R.id.action_sync_now -> SyncJob.forceSync()
            R.id.action_sync_log -> showSyncLog()
            R.id.action_create_filter -> createDynalistItemFilter()
            R.id.action_search -> searchDynalistItem()
        }
        drawer_layout?.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showSyncLog() {
        val handler = Handler(Looper.getMainLooper())
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.activity_vertical_margin)
        val sectionSpacing = resources.getDimensionPixelSize(R.dimen.list_item_padding)
        val currentStateText = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 15f
        }
        val historyText = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            addView(TextView(this@NavigationActivity).apply {
                text = "Current state"
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(currentStateText, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = sectionSpacing * 2
            })
            addView(TextView(this@NavigationActivity).apply {
                text = "History"
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(ScrollView(this@NavigationActivity).apply {
                addView(historyText)
            }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.displayMetrics.heightPixels / 3
            ))
        }
        val refresh = object : Runnable {
            override fun run() {
                val report = SyncLog.buildReport(this@NavigationActivity)
                currentStateText.text = report.currentState
                historyText.text = report.history
                handler.postDelayed(this, 1_000L)
            }
        }
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.sync_log_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        dialog.setOnDismissListener { handler.removeCallbacks(refresh) }
        refresh.run()
    }

    private fun createDynalistItemFilter() {
        Intent(this, EditFilterActivity::class.java).apply {
            val requestCode = resources.getInteger(R.integer.request_code_create_filter)
            putExtra(DynalistApp.EXTRA_DISPLAY_FILTER, DynalistItemFilter())
            startActivityForResult(this, requestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                resources.getInteger(R.integer.request_code_create_filter) -> {
                    val filter = data!!.getParcelableExtra<DynalistItemFilter>(
                            DynalistApp.EXTRA_DISPLAY_FILTER)!!
                    openDynalistItemFilter(filter)
                }
            }
        }
    }

    private fun searchDynalistItem() {
        val searchIntent = Intent(SearchActivity.ACTION_SEARCH_DISPLAY_ITEM)
        val transition = ActivityOptions.makeSceneTransitionAnimation(
                this, appBar, "toolbar")
        startActivity(searchIntent, transition.toBundle())
    }

    private fun openSettings() {
        Intent(this, SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun rateQuickDynalist() {
        val uri = Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            val browserUri = Uri.parse("https://play.google.com/store/apps/details?id="
                    + BuildConfig.APPLICATION_ID)
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    private fun shareQuickDynalist() {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_quickdynalist_text))
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(R.string.share_quickdynalist)))
        }
    }

    private fun replaceMainUiFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.findFragmentByTag(MAIN_UI_FRAGMENT)?.let { remove(it) }
            add(R.id.fragment_container, fragment, MAIN_UI_FRAGMENT)
            addToBackStack(null)
            commit()
        }
    }

    private fun openDynalistItem(itemToOpen: DynalistItem) {
        val fragment = ItemListFragment.newInstance(itemToOpen)
        replaceMainUiFragment(fragment)
    }

    private fun openDynalistItemFilter(filterToOpen: DynalistItemFilter) {
        val fragment = FilteredItemListFragment.newInstance(filterToOpen)
        replaceMainUiFragment(fragment)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success) {
            if (event.retrying)
                toast(R.string.error_update_server_retry)
            else
                toast(R.string.error_update_server)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDynalistLocateEvent(event: DynalistLocateEvent) {
        openDynalistItem(event.item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDynalistFilterEvent(event: DynalistFilterEvent) {
        openDynalistItemFilter(event.filter)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSyncEvent(event: SyncEvent) {
        when (event.status) {
            SyncStatus.RUNNING -> nav_view.menu.findItem(R.id.action_sync_now).apply {
                setIcon(R.drawable.ic_action_sync)
                isEnabled = false
                setTitle(R.string.sync_in_progress)
            }
            SyncStatus.NOT_RUNNING -> nav_view.menu.findItem(R.id.action_sync_now).apply {
                setIcon(R.drawable.ic_action_sync)
                isEnabled = true
                setTitle(R.string.action_sync_now)
            }
            else -> Unit
        }
    }

    override fun onNightModeChanged(mode: Int) {
        super.onNightModeChanged(mode)
        EventBus.getDefault().post(NightModeChangedEvent())
        // TODO we have to refresh the view model to get new spannables
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSyncProgressEvent(event: SyncProgressEvent) {
        val menuItem = nav_view.menu.findItem(R.id.action_sync_now)
        val icon = if (menuItem.icon !is CircularProgressDrawable) {
            CircularProgressDrawable(this).also {
                it.setStyle(CircularProgressDrawable.DEFAULT)
                it.alpha = 0xCC
                it.setColorFilter(resolveColorAttribute(R.attr.colorAccent), PorterDuff.Mode.SRC_IN)
                menuItem.icon = it
            }
        } else {
            menuItem.icon as CircularProgressDrawable
        }
        icon.setStartEndTrim(0f, event.progress)
    }
}
