/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2013  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fedorahosted.freeotp.Token.TokenUriInvalidException;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TokenAdapter extends BaseAdapter {
	private static class Ticker extends Handler {
		private static interface OnTickListener {
			public void tick(ProgressBar pb);
		}

		private final Map<ProgressBar, OnTickListener> map = new HashMap<ProgressBar, OnTickListener>();

		@Override
		public void handleMessage(Message msg) {
			for (ProgressBar pb : map.keySet())
				map.get(pb).tick(pb);

			sendEmptyMessageDelayed(0, 200);
		}

		public void set(ProgressBar pb, OnTickListener otl) {
			map.put(pb, otl);
		}
	}

	private final List<Token> tokens = new ArrayList<Token>();
	private final Ticker ticker = new Ticker();

	private void sort() {
		Collections.sort(tokens, new Comparator<Token>() {
			@Override
			public int compare(Token lhs, Token rhs) {
				return lhs.getTitle().compareTo(rhs.getTitle());
			}
		});
	}

	public TokenAdapter(Context ctx) {
		tokens.addAll(Token.getTokens(ctx));
		ticker.sendEmptyMessageDelayed(0, 200);
		sort();
	}

	@Override
	public int getCount() {
		return tokens.size();
	}

	@Override
	public Token getItem(int position) {
		return tokens.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final Context ctx = parent.getContext();

		if (convertView == null) {
			switch (getItem(position).getType()) {
			case HOTP:
				convertView = View.inflate(ctx, R.layout.hotp, null);
				break;

			case TOTP:
				convertView = View.inflate(ctx, R.layout.totp, null);
				break;
			}
		}

		final Token item = getItem(position);
		final TextView code = (TextView) convertView.findViewById(R.id.code);
		final TextView title = (TextView) convertView.findViewById(R.id.title);
		final ImageButton ib = (ImageButton) convertView.findViewById(R.id.button);

		code.setText(item.getCurrentTokenValue(ctx, false));
		title.setText(item.getTitle());

		ib.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String delmsg = ctx.getString(R.string.delete_message);

				AlertDialog ad = new AlertDialog.Builder(ctx)
				.setTitle("Delete")
				.setMessage(delmsg + item.getTitle())
				.setIcon(android.R.drawable.ic_delete)
				.setPositiveButton(R.string.delete,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								tokens.remove(tokens.indexOf(item));
								item.remove(ctx);
								notifyDataSetChanged();
								dialog.dismiss();
							}

						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}
						}).create();
				ad.show();
			}
		});

		switch (getItem(position).getType()) {
		case HOTP:
			ImageButton hotp = (ImageButton) convertView.findViewById(R.id.hotpButton);
			hotp.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					code.setText(item.getCurrentTokenValue(ctx, true));
				}
			});
			break;

		case TOTP:
			ProgressBar pb = (ProgressBar) convertView.findViewById(R.id.totpProgressBar);
			ticker.set(pb, new Ticker.OnTickListener() {
				@Override
				public void tick(ProgressBar pb) {
					int max = pb.getMax();
					int pro = item.getProgress();
					pb.setProgress(max - pro);
					if (pro < max / 20 || pro > max / 20 * 19)
						code.setText(item.getCurrentTokenValue(ctx, false));
				}
			});
			break;
		}

		return convertView;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		switch (getItem(position).getType()) {
		case HOTP:
			return 0;
		case TOTP:
			return 1;
		default:
			return -1;
		}
	}

	public void add(Context ctx, String uri) throws TokenUriInvalidException {
		Token t = new Token(uri);
		t.save(ctx);
		tokens.add(t);
		sort();
		notifyDataSetChanged();
	}
}