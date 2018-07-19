package com.hochan.dragtofinishphotoview;

import android.app.SharedElementCallback;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.hochan.dragtofinishphotoview.databinding.ActivityFullScreenImageBinding;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class FullScreenImageActivity extends AppCompatActivity implements DragToFinishPhotoView.OnPhotoViewDragListener {

	public static final String EXTRA_DEFAULT_INDEX = "index";
	public static final String EXTRA_EXIT_INDEX = "exit_index";

	private ActivityFullScreenImageBinding mViewBinding;
	private WeakHashMap<String, View> mTransitionNameToView = new WeakHashMap<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mViewBinding = ActivityFullScreenImageBinding.inflate(getLayoutInflater());
		setContentView(mViewBinding.getRoot());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
		}

		final int defaultIndex = getIntent().getIntExtra(EXTRA_DEFAULT_INDEX, 0);

		supportPostponeEnterTransition();
		mViewBinding.viewPager.setAdapter(new PagerAdapter() {
			@Override
			public int getCount() {
				return Photo.PHOTO_LIST.length;
			}

			@Override
			public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
				return view == object;
			}

			@NonNull
			@Override
			public Object instantiateItem(@NonNull ViewGroup container, final int position) {
				final DragToFinishPhotoView photoView = new DragToFinishPhotoView(getApplicationContext());
				container.addView(photoView);
				photoView.setOnPhotoViewDragListener(FullScreenImageActivity.this);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					photoView.setTransitionName(String.valueOf(position));
					mTransitionNameToView.put(String.valueOf(position), photoView);
				}
				GlideApp.with(FullScreenImageActivity.this).load(Photo.PHOTO_LIST[position])
						.onlyRetrieveFromCache(true)
						.listener(new RequestListener<Drawable>() {
							@Override
							public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
								return false;
							}

							@Override
							public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
								if (position == defaultIndex) {
									photoView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
										@Override
										public boolean onPreDraw() {
											photoView.getViewTreeObserver().removeOnPreDrawListener(this);
											supportStartPostponedEnterTransition();
											return false;
										}
									});
								}
								return false;
							}
						}).into(photoView);
				return photoView;
			}

			@Override
			public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
				container.removeView((View) object);
				mTransitionNameToView.remove(String.valueOf(position));
			}
		});
		mViewBinding.viewPager.setCurrentItem(defaultIndex);
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onDragToFinish() {
		finishAfterTransition();
	}

	@Override
	public void onDragOffset(float offset, float maxOffset) {
		mViewBinding.vBgMask.setAlpha(1 - offset / maxOffset);
	}

	@Override
	public void finishAfterTransition() {
		Intent intent = new Intent();
		intent.putExtra(EXTRA_EXIT_INDEX, mViewBinding.viewPager.getCurrentItem());
		setResult(RESULT_OK, intent);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			setEnterSharedElementCallback(new SharedElementCallback() {
				@Override
				public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
					names.clear();
					sharedElements.clear();
					names.add(String.valueOf(mViewBinding.viewPager.getCurrentItem()));
					View view = mTransitionNameToView.get(String.valueOf(mViewBinding.viewPager.getCurrentItem()));
					if (view != null) {
						sharedElements.put(String.valueOf(mViewBinding.viewPager.getCurrentItem()), view);
					}
				}
			});
		}
		super.finishAfterTransition();
	}
}
