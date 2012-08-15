package com.florianmski.tracktoid.ui.fragments.library;

import java.util.List;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.ActionMode.Callback;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.florianmski.tracktoid.ListCheckerManager;
import com.florianmski.tracktoid.R;
import com.florianmski.tracktoid.Utils;
import com.florianmski.tracktoid.adapters.GridPosterAdapter;
import com.florianmski.tracktoid.events.TraktItemsRemovedEvent;
import com.florianmski.tracktoid.events.TraktItemsUpdatedEvent;
import com.florianmski.tracktoid.image.TraktImage;
import com.florianmski.tracktoid.trakt.tasks.post.InCollectionTask;
import com.florianmski.tracktoid.trakt.tasks.post.InWatchlistTask;
import com.florianmski.tracktoid.trakt.tasks.post.SeenTask;
import com.florianmski.tracktoid.ui.fragments.TraktFragment;
import com.florianmski.tracktoid.widgets.CheckableGridView;
import com.florianmski.traktoid.TraktoidInterface;
import com.squareup.otto.Subscribe;

public abstract class PI_LibraryFragment<T extends TraktoidInterface<T>> extends TraktFragment
{
	protected final static int NB_COLUMNS_TABLET_PORTRAIT = 5;
	protected final static int NB_COLUMNS_TABLET_LANDSCAPE = 7;
	protected final static int NB_COLUMNS_PHONE_PORTRAIT = 3;
	protected final static int NB_COLUMNS_PHONE_LANDSCAPE = 5;

	protected CheckableGridView<T> gd;
	protected GridPosterAdapter<T> adapter;
	private FrameLayout flProgress;
	
	protected ListCheckerManager<T> lcm;
	
	private boolean refresh = false;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	public abstract boolean checkUpdateTask();
	public abstract GridPosterAdapter<T> setupAdapter();
	public abstract void onGridItemClick(AdapterView<?> arg0, View v, int position, long arg3);
	public abstract void displayContent();
	public abstract void onRefreshClick();

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		lcm = new ListCheckerManager<T>();
		lcm.setNoSelectedColorResId(R.drawable.selector_no_background);
		lcm.setSelectedViewResId(R.id.relativeLayoutSelectorPurpose);
		lcm.setOnActionModeListener(new Callback() 
		{
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) 
			{
				return false;
			}
			
			@Override
			public void onDestroyActionMode(ActionMode mode) 
			{
				
			}
			
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) 
			{
				SubMenu seenMenu = menu.addSubMenu("watched");
				seenMenu.add(0, R.id.action_bar_watched_seen, 0, "Seen")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				seenMenu.add(0, R.id.action_bar_watched_unseen, 0, "Unseen")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				MenuItem seenItem = seenMenu.getItem();
		        seenItem.setIcon(R.drawable.ab_icon_eye);
		        seenItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
				
		        SubMenu watchlistMenu = menu.addSubMenu("watchlist");
		        watchlistMenu.add(0, R.id.action_bar_add_to_watchlist, 0, "add to watchlist")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		        watchlistMenu.add(0, R.id.action_bar_remove_from_watchlist, 0, "remove from watchlist")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				MenuItem watchlistItem = watchlistMenu.getItem();
				watchlistItem.setIcon(R.drawable.badge_watchlist);
				watchlistItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
		        
				SubMenu collectionMenu = menu.addSubMenu("collection");
				collectionMenu.add(0, R.id.action_bar_add_to_collection, 0, "add to collection")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				collectionMenu.add(0, R.id.action_bar_remove_from_collection, 0, "remove from collection")
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				MenuItem collectionItem = collectionMenu.getItem();
				collectionItem.setIcon(R.drawable.badge_collection);
				collectionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
				return true;
			}
			
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) 
			{
				switch(item.getItemId())
				{
				case R.id.action_bar_watched_unseen:
				case R.id.action_bar_watched_seen:
					SeenTask.createTask(getActivity(), lcm.getItemsList(), item.getItemId() == R.id.action_bar_watched_seen, null).fire();
					break;
				case R.id.action_bar_add_to_watchlist:
				case R.id.action_bar_remove_from_watchlist:
					InWatchlistTask.createTask(getActivity(), lcm.getItemsList(), item.getItemId() == R.id.action_bar_add_to_watchlist, null).fire();
					break;
				case R.id.action_bar_add_to_collection:
				case R.id.action_bar_remove_from_collection:
					InCollectionTask.createTask(getActivity(), lcm.getItemsList(), item.getItemId() == R.id.action_bar_add_to_collection, null).fire();
					break;
				}
				return true;
			}
		});
		
		lcm.addListener(gd);
		gd.initialize(this, 0, lcm);
		
		if(lcm.isActivated())
			getSherlockActivity().startActionMode(lcm.getCallback());
		
		getStatusView().show().text("Loading ,\nPlease wait...");

		refreshGridView();

		adapter = setupAdapter();
		gd.setAdapter(adapter);

		displayContent();

		gd.setOnItemClickListener(new OnItemClickListener() 
		{
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3)
			{
				onGridItemClick(arg0, arg1, position, arg3);
			}
		});
	}

	@Override
	public void onDestroy()
	{
		lcm.removeListener(gd);
		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle toSave) {}

	@SuppressWarnings("unchecked")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
		View v = inflater.inflate(R.layout.pager_item_library, null);

		gd = (CheckableGridView<T>)v.findViewById(R.id.gridViewLibrary);

		return v;
	}

	public int refreshGridView()
	{
		int nbColumns;

		if(Utils.isTabletDevice(getActivity()))
		{
			if(Utils.isLandscape(getActivity()))
				nbColumns = NB_COLUMNS_TABLET_LANDSCAPE;
			else
				nbColumns = NB_COLUMNS_TABLET_PORTRAIT;	
		}
		else
		{
			if(Utils.isLandscape(getActivity()))
				nbColumns = NB_COLUMNS_PHONE_LANDSCAPE;
			else
				nbColumns = NB_COLUMNS_PHONE_PORTRAIT;	
		}

		gd.setNumColumns(nbColumns);

		if(adapter != null)
			adapter.setHeight(calculatePosterHeight(nbColumns));

		return calculatePosterHeight(nbColumns);
	}

	@SuppressWarnings("deprecation")
	private int calculatePosterHeight(int nbColumns)
	{
		int width = (getActivity().getWindowManager().getDefaultDisplay().getWidth()/(nbColumns));
		return (int) (width*TraktImage.RATIO_POSTER);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);

		SubMenu filterMenu = menu.addSubMenu(0, R.id.action_bar_filter, 0, "Filter");
		filterMenu.add(0, R.id.action_bar_filter_all, 0, "All");
		filterMenu.add(0, R.id.action_bar_filter_unwatched, 0, "Unwatched");
//		filterMenu.add(0, R.id.action_bar_filter_loved, 0, "Loved");

		MenuItem rateItem = filterMenu.getItem();
		rateItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
		
		menu.add(0, R.id.action_bar_multiple_selection, 0, "Multiple selection")
		.setIcon(R.drawable.ab_icon_mark)
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		
		if(flProgress == null)
			createProgressItem();
		
		MenuItem refreshItem = menu.add(Menu.NONE, R.id.action_bar_refresh, 0, "Refresh");
		refreshItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		if(refresh || checkUpdateTask())
			refreshItem.setActionView(flProgress);
		else
			refreshItem.setIcon(R.drawable.ab_icon_refresh);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		switch(item.getItemId())
		{
		case R.id.action_bar_refresh:
			setRefresh(true);
			onRefreshClick();
			break;
		case R.id.action_bar_filter_all:
			adapter.setFilter(GridPosterAdapter.FILTER_ALL);
			break;
		case R.id.action_bar_filter_unwatched:
			adapter.setFilter(GridPosterAdapter.FILTER_UNWATCHED);
			break;
		case R.id.action_bar_multiple_selection:
			gd.start();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Subscribe
	public void onTraktItemsUpdated(TraktItemsUpdatedEvent<T> event) 
	{
		List<T> traktItems = event.getTraktItems(this);
		if(adapter != null && traktItems != null)
			adapter.updateItems(traktItems);
	}
	
	@Subscribe
	public void onTraktItemsRemoved(TraktItemsRemovedEvent<T> event) 
	{
		List<T> traktItems = event.getTraktItems(this);
		if(adapter != null && traktItems != null)
			adapter.updateItems(traktItems);
	}
	
	public void createProgressItem()
	{
		flProgress = new FrameLayout(getSherlockActivity());
		ProgressBar pb = new ProgressBar(getSherlockActivity());
		pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.progress));
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
		lp.setMargins(px/2, px, px/2, px);
		pb.setLayoutParams(lp);
		flProgress.addView(pb);
		flProgress.setClickable(true);
		flProgress.setBackgroundResource(R.drawable.abs__item_background_holo_light);
	}
	
	public void setRefresh(boolean on)
	{
		this.refresh = on;
		getSherlockActivity().invalidateOptionsMenu();
		
		if(on)
			getStatusView().hide().text(null);
	}
}
