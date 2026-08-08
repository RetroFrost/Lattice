/*
 * Lattice additions to Telegram for Android.
 * Licensed under GNU GPL v2 or later, matching the upstream project.
 */
package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LatticeChatPreferences;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Local-only Lattice setting which makes selected group/channel dialogs open
 * directly in Telegram's native Files tab instead of the message timeline.
 */
public class LatticeFilesOnlyActivity extends BaseFragment {
    private static final int VIEW_INFO = 0;
    private static final int VIEW_SELECT = 1;
    private static final int VIEW_CHAT = 2;
    private static final int VIEW_SHADOW = 3;
    private static final int VIEW_CLEAR = 4;

    private final ArrayList<Long> selectedDialogs = new ArrayList<>();
    private RecyclerListView listView;
    private Adapter adapter;

    @Override
    public boolean onFragmentCreate() {
        reloadSelection();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle("Files-only chats");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter(context));
        listView.setOnItemClickListener((view, position) -> {
            int type = adapter.getItemViewType(position);
            if (type == VIEW_SELECT) {
                openSelector();
            } else if (type == VIEW_CHAT) {
                int index = position - 2;
                if (index >= 0 && index < selectedDialogs.size()) {
                    LatticeChatPreferences.setFilesOnly(selectedDialogs.get(index), false);
                    reloadSelection();
                    adapter.notifyDataSetChanged();
                }
            } else if (type == VIEW_CLEAR) {
                LatticeChatPreferences.clearFilesOnlyDialogs();
                reloadSelection();
                adapter.notifyDataSetChanged();
            }
        });
        root.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadSelection();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void reloadSelection() {
        selectedDialogs.clear();
        selectedDialogs.addAll(LatticeChatPreferences.getFilesOnlyDialogs());
    }

    private void openSelector() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_WIDGET);
        args.putBoolean("allowUsers", false);
        args.putBoolean("allowBots", false);
        args.putBoolean("allowGroups", true);
        args.putBoolean("allowMegagroups", true);
        args.putBoolean("allowLegacyGroups", true);
        args.putBoolean("allowChannels", true);
        args.putBoolean("allowGlobalSearch", false);
        args.putBoolean("canSelectTopics", false);

        DialogsActivity picker = new DialogsActivity(args);
        picker.setLatticePreselectedDialogs(new ArrayList<>(selectedDialogs));
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            LatticeChatPreferences.clearFilesOnlyDialogs();
            for (int i = 0; i < dids.size(); i++) {
                long dialogId = dids.get(i).dialogId;
                if (dialogId < 0) {
                    LatticeChatPreferences.setFilesOnly(dialogId, true);
                }
            }
            fragment.finishFragment();
            reloadSelection();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            return true;
        });
        presentFragment(picker);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_SELECT || type == VIEW_CHAT || type == VIEW_CLEAR;
        }

        @Override
        public int getItemCount() {
            return 3 + selectedDialogs.size() + (selectedDialogs.isEmpty() ? 0 : 1);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return VIEW_INFO;
            if (position == 1) return VIEW_SELECT;
            if (position >= 2 && position < 2 + selectedDialogs.size()) return VIEW_CHAT;
            if (position == 2 + selectedDialogs.size()) return VIEW_SHADOW;
            return VIEW_CLEAR;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_INFO) {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                cell.setText("Selected groups, supergroups and channels open directly in Telegram's Files view. This preference stays only on this device. Tap a selected chat to remove it.");
                view = cell;
            } else if (viewType == VIEW_SELECT) {
                TextCell cell = new TextCell(context);
                cell.setTextAndIcon("Select groups and channels", R.drawable.msg_settings, false);
                cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                view = cell;
            } else if (viewType == VIEW_CHAT) {
                view = new UserCell(context, 4, 0, false, false);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_CLEAR) {
                TextCell cell = new TextCell(context);
                cell.setText("Clear files-only chats", false);
                cell.setColors(-1, Theme.key_text_RedRegular);
                view = cell;
            } else {
                view = new ShadowSectionCell(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != VIEW_CHAT) {
                return;
            }
            long dialogId = selectedDialogs.get(position - 2);
            TLObject object = getMessagesController().getUserOrChat(dialogId);
            String title = "Group or channel";
            if (object instanceof TLRPC.Chat) {
                title = ((TLRPC.Chat) object).title;
            }
            UserCell cell = (UserCell) holder.itemView;
            cell.setData(object, title, "Files only", 0, true);
        }
    }
}
