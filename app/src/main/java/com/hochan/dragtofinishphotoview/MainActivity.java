package com.hochan.dragtofinishphotoview;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import com.hochan.dragtofinishphotoview.databinding.ActivityMainBinding;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

	public static void setStatusBarHalfTranslucent(Window window) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		}
	}

	public static void setNavigationBarTranslucent(Window window) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
		}
	}

	private int mEnterPosition;
	private int mExitPosition;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ActivityMainBinding viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(viewBinding.getRoot());

		setStatusBarHalfTranslucent(getWindow());
		setNavigationBarTranslucent(getWindow());

		viewBinding.recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), 3));
		viewBinding.recyclerView.setAdapter(mAdapter);
		setExitSharedElementCallback(new SharedElementCallback() {
			@Override
			public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
				if (mExitPosition != mEnterPosition) {
					names.clear();
					sharedElements.clear();
					names.add(String.valueOf(mExitPosition));
					try {
						sharedElements.put(String.valueOf(mExitPosition),
								viewBinding.recyclerView.findViewHolderForAdapterPosition(mExitPosition).itemView);
					} catch (Exception ignored) {

					}
				}
			}
		});
	}

	private RecyclerView.Adapter mAdapter = new RecyclerView.Adapter() {
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ImageView imageView = new ImageView(getApplicationContext());
			imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
			return new ItemViewHolder(imageView);
		}

		@Override
		public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
			final ImageView imageView = (ImageView) holder.itemView;
			int screenWidth = getApplicationContext().getResources().getDisplayMetrics().widthPixels;
			imageView.setLayoutParams(new ViewGroup.LayoutParams(screenWidth / 3, screenWidth / 3));
			imageView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), android.R.color.white));
			GlideApp.with(MainActivity.this).load(Photo.PHOTO_LIST[position]).into(imageView);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				imageView.setTransitionName(String.valueOf(position));
			}
			imageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(MainActivity.this, FullScreenImageActivity.class);
					intent.putExtra(FullScreenImageActivity.EXTRA_DEFAULT_INDEX, holder.getAdapterPosition());
					mEnterPosition = mExitPosition = holder.getAdapterPosition();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this,
								imageView, imageView.getTransitionName()).toBundle());
					}
				}
			});
		}

		@Override
		public int getItemCount() {
			return Photo.PHOTO_LIST.length;
		}
	};

	@Override
	public void onActivityReenter(int resultCode, Intent data) {
		if (resultCode == RESULT_OK && data != null) {
			mExitPosition = data.getIntExtra(FullScreenImageActivity.EXTRA_EXIT_INDEX, -1);
		}
	}

	class ItemViewHolder extends RecyclerView.ViewHolder {

		ItemViewHolder(View itemView) {
			super(itemView);
		}
	}
}
