/**
 * Copyright 2011 Kurtis L. Nusbaum
 * 
 * This file is part of UDJ.
 * 
 * UDJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * UDJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with UDJ.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.klnusbaum.udj;

import org.klnusbaum.udj.PullToRefresh.RefreshableListFragment;
import org.klnusbaum.udj.network.PlaylistSyncService;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Class used for displaying the contents of the Playlist.
 */
public class PlaylistFragment extends RefreshableListFragment implements
    LoaderManager.LoaderCallbacks<Cursor> {
  private static final String TAG = "PlaylistFragment";
  private static final int PLAYLIST_LOADER_ID = 0;
  private Account account;
  private long userId;
  private AccountManager am;
  /**
   * Adapter used to help display the contents of the playlist.
   */
  PlaylistAdapter playlistAdapter;

  @Override
  protected void doRefreshWork() {
    ((PlayerActivity) getActivity()).getPlaylistFromServer();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    account = Utils.basicGetUdjAccount(getActivity());
    am = AccountManager.get(getActivity());
    userId = Long.valueOf(am.getUserData(account, Constants.USER_ID_DATA));
    setEmptyText(getActivity().getString(R.string.no_playlist_items));
    playlistAdapter = new PlaylistAdapter(getActivity(), null, userId);
    setListAdapter(playlistAdapter);
    setListShown(false);
    getLoaderManager().initLoader(PLAYLIST_LOADER_ID, null, this);
    registerForContextMenu(getListView());

  }

  public void onListItemClick(ListView l, View v, int position, long id) {
    l.showContextMenuForChild(v);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenu.ContextMenuInfo menuInfo) {
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
    Cursor song = (Cursor) playlistAdapter.getItem(info.position);
    MenuInflater inflater = getActivity().getMenuInflater();
    inflater.inflate(R.menu.playlist_context, menu);
    if (song.getLong(song.getColumnIndex(UDJPlayerProvider.ADDER_ID_COLUMN)) == userId) {
      menu.findItem(R.id.vote_up).setEnabled(false);
      menu.findItem(R.id.vote_down).setEnabled(false);
    } else if (!song.isNull(song
        .getColumnIndex(UDJPlayerProvider.DID_VOTE_COLUMN))) {
      int voteType = song.getInt(song
          .getColumnIndex(UDJPlayerProvider.DID_VOTE_COLUMN));
      if (voteType == 1) {
        menu.findItem(R.id.vote_up).setEnabled(false);
        menu.findItem(R.id.remove_song).setEnabled(false);
      } else {
        menu.findItem(R.id.vote_down).setEnabled(false);
        menu.findItem(R.id.remove_song).setEnabled(false);
      }
    } else {
      menu.findItem(R.id.remove_song).setEnabled(false);
    }
    int titleIndex = song.getColumnIndex(UDJPlayerProvider.TITLE_COLUMN);
    menu.setHeaderTitle(song.getString(titleIndex));
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
        .getMenuInfo();
    switch (item.getItemId()) {
    case R.id.share:
      shareSong(info.position);
      return true;
    case R.id.vote_up:
      upVoteSong(info.position);
      return true;
    case R.id.vote_down:
      downVoteSong(info.position);
      return true;
    case R.id.remove_song:
      removeSong(info.position);
      return true;
    default:
      return super.onContextItemSelected(item);
    }
  }

  private void shareSong(int position) {
    Cursor toShare = (Cursor) playlistAdapter.getItem(position);
    int titleIndex = toShare.getColumnIndex(UDJPlayerProvider.TITLE_COLUMN);
    String songTitle = toShare.getString(titleIndex);
    String playerName = am.getUserData(account, Constants.PLAYER_NAME_DATA);
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
        getString(R.string.song_share_1) + " " + songTitle + " "
            + getString(R.string.song_share_2) + " " + playerName
            + ".");
    startActivity(Intent.createChooser(shareIntent,
        getString(R.string.share_via)));

  }

  private void removeSong(int position) {
    Cursor toRemove = (Cursor) playlistAdapter.getItem(position);
    int idIndex = toRemove
        .getColumnIndex(UDJPlayerProvider.LIB_ID_COLUMN);
    Log.d(TAG, "Removing song with id " + toRemove.getLong(idIndex));
    Intent removeSongIntent = new Intent(
      Intent.ACTION_DELETE,
      UDJPlayerProvider.PLAYLIST_URI,
      getActivity(),
      PlaylistSyncService.class);
    removeSongIntent.putExtra(Constants.ACCOUNT_EXTRA, account);
    removeSongIntent.putExtra(Constants.LIB_ID_EXTRA, toRemove.getLong(idIndex));
    getActivity().startService(removeSongIntent);
    Toast toast = Toast.makeText(getActivity(),
        getString(R.string.removing_song), Toast.LENGTH_SHORT);
    toast.show();
  }

  private void upVoteSong(int position) {
    voteOnSong(position, 1);
  }

  private void downVoteSong(int position) {
    voteOnSong(position, -1);
  }

  private void voteOnSong(int position, int voteType) {
    Cursor song = (Cursor) playlistAdapter.getItem(position);
    int idIndex = song.getColumnIndex(UDJPlayerProvider.LIB_ID_COLUMN);
    long libId = song.getLong(idIndex);
    Intent voteIntent = new Intent(Intent.ACTION_INSERT,
        UDJPlayerProvider.VOTES_URI, getActivity(),
        PlaylistSyncService.class);
    voteIntent.putExtra(Constants.ACCOUNT_EXTRA, account);
    voteIntent.putExtra(Constants.VOTE_WEIGHT_EXTRA, voteType);
    voteIntent.putExtra(Constants.LIB_ID_EXTRA, libId);
    getActivity().startService(voteIntent);

  }

  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    switch (id) {
    case PLAYLIST_LOADER_ID:
      Uri playlistUri = UDJPlayerProvider.PLAYLIST_URI.buildUpon().appendQueryParameter(
          UDJPlayerProvider.USER_ID_PARAM, String.valueOf(userId)).build();
      return new CursorLoader(getActivity(),
          playlistUri, null, null, null,
          UDJPlayerProvider.PRIORITY_COLUMN);
    default:
      return null;
    }
  }

  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    if (loader.getId() == PLAYLIST_LOADER_ID) {
      refreshDone();
      playlistAdapter.swapCursor(data);
      if (isResumed()) {
        setListShown(true);
      } else if (isVisible()) {
        setListShownNoAnimation(true);
      }
    }
  }

  public void onLoaderReset(Loader<Cursor> loader) {
    if (loader.getId() == PLAYLIST_LOADER_ID) {
      playlistAdapter.swapCursor(null);
    }
  }

  private class PlaylistAdapter extends CursorAdapter {
    private long userId;
    private static final String PLAYLIST_ADAPTER_TAG = "PlaylistAdapter";

    public PlaylistAdapter(Context context, Cursor c, long userId) {
      super(context, c, 0);
      this.userId = userId;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {


      TextView songName = (TextView) view
          .findViewById(R.id.playlistSongName);
      int titleIndex = cursor
          .getColumnIndex(UDJPlayerProvider.TITLE_COLUMN);
      songName.setText(cursor.getString(titleIndex));

      TextView artist = (TextView) view
          .findViewById(R.id.playlistArtistName);
      int artistIndex = cursor
          .getColumnIndex(UDJPlayerProvider.ARTIST_COLUMN);
      artist.setText(getString(R.string.by) + " "
          + cursor.getString(artistIndex));

      TextView addByUser = (TextView) view
          .findViewById(R.id.playlistAddedBy);
      int adderIdIndex = cursor
          .getColumnIndex(UDJPlayerProvider.ADDER_ID_COLUMN);
      if (cursor.getLong(adderIdIndex) == userId) {
        addByUser.setText(getString(R.string.added_by) + " "
            + getString(R.string.you));
      } else {
        int adderUserNameIndex = cursor
            .getColumnIndex(UDJPlayerProvider.ADDER_USERNAME_COLUMN);
        addByUser.setText(getString(R.string.added_by) + " "
            + cursor.getString(adderUserNameIndex));
      }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater) context
          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      View itemView = inflater.inflate(R.layout.playlist_list_item, null);
      return itemView;
    }

  }

}
