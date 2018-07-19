package com.hochan.dragtofinishphotoview;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import java.lang.ref.WeakReference;

import static android.view.MotionEvent.INVALID_POINTER_ID;

public class DragToFinishPhotoView extends android.support.v7.widget.AppCompatImageView implements View.OnLayoutChangeListener {

	private static float DEFAULT_MAX_SCALE = 3.0f;
	private static float DEFAULT_MID_SCALE = 1.75f;
	private static float DEFAULT_MIN_SCALE = 1.0f;

	private int mMinimumVelocity;
	private final float mTouchSlop;

	private float mMinScale = DEFAULT_MIN_SCALE;
	private float mMidScale = DEFAULT_MID_SCALE;
	private float mMaxScale = DEFAULT_MAX_SCALE;

	private final Matrix mDragDownMatrix = new Matrix();
	private final Matrix mDrawMatrix = new Matrix();
	private final Matrix mBaseMatrix = new Matrix();
	private final Matrix mScaleAndDragMatrix = new Matrix();
	private RectF mDisplayRect = new RectF();
	private final float[] mMatrixValues = new float[9];

	private int mActivePointerId;
	private int mActivePointerIndex = 0;
	private float mActionDownX;
	private float mActionDownY;
	private float mDoubleTapTouchX;
	private float mDoubleTapTouchY;
	private float mLastTouchX;
	private float mLastTouchY;
	private boolean mIsDragging = false;
	private boolean mIsDraggingDown = false;

	private VelocityTracker mVelocityTracker;
	private GestureDetector mGestureDetector;
	private ScaleGestureDetector mScaleGestureDetector;

	private FlingRunnable mCurrentFlingRunnable;
	private TranslateUpAnimator mTranslateUpAnimator;

	private OnPhotoViewDragListener mOnPhotoViewDragListener;

	public DragToFinishPhotoView(Context context) {
		this(context, null);
	}

	public DragToFinishPhotoView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public DragToFinishPhotoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setScaleType(ScaleType.MATRIX);
		addOnLayoutChangeListener(this);

		ViewConfiguration configuration = ViewConfiguration.get(getContext());
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mTouchSlop = configuration.getScaledTouchSlop();

		// 监听缩放手势
		setUpScaleGestureDetector();
		// 监听单击和双击手势
		setUpGestureDetector();
	}

	private void setUpGestureDetector() {
		mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				if (mOnPhotoViewDragListener != null) {
					mOnPhotoViewDragListener.onDragToFinish();
				}
				return true;
			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				if (getMatrixScale() < mMidScale) {
					mDoubleTapTouchX = e.getX();
					mDoubleTapTouchY = e.getY();
					startZoomAnimation(DragToFinishPhotoView.this, getMatrixScale(),
							mMidScale, mDoubleTapTouchX, mDoubleTapTouchY);
				} else if (getMatrixScale() >= mMidScale && getMatrixScale() < mMaxScale) {
					startZoomAnimation(DragToFinishPhotoView.this, getMatrixScale(),
							mMaxScale, mDoubleTapTouchX, mDoubleTapTouchY);
				} else {
					startZoomAnimation(DragToFinishPhotoView.this, getMatrixScale(),
							mMinScale, mDoubleTapTouchX, mDoubleTapTouchY);
				}
				return true;
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				if (!mIsDraggingDown) {
					mCurrentFlingRunnable = new FlingRunnable(DragToFinishPhotoView.this);
					mCurrentFlingRunnable.fling(getViewWidth(), getViewHeight(), (int) -velocityX, (int) -velocityY);
					post(mCurrentFlingRunnable);
				}
				return true;
			}
		});
	}

	private void setUpScaleGestureDetector() {
		mScaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.OnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
				if (getMatrixScale() > 1 || scaleGestureDetector.getScaleFactor() > 1f) {
					mScaleAndDragMatrix.postScale(scaleGestureDetector.getScaleFactor(),
							scaleGestureDetector.getScaleFactor(),
							scaleGestureDetector.getFocusX(),
							scaleGestureDetector.getFocusY());
					updateMatrix();
				}
				return true;
			}

			@Override
			public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
				if (mIsDraggingDown) {
					mIsDraggingDown = false;
					mTranslateUpAnimator = new TranslateUpAnimator(DragToFinishPhotoView.this,
							getMatrixValue(mDragDownMatrix, Matrix.MTRANS_Y), 0);
				}
				recycleVelocityTracker();
				return true;
			}

			@Override
			public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
				// 啥也不做
			}
		});
	}

	@SuppressWarnings("unused")
	public void setOnPhotoViewDragListener(OnPhotoViewDragListener listener) {
		mOnPhotoViewDragListener = listener;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mScaleGestureDetector != null) {
			mScaleGestureDetector.onTouchEvent(event);
		}
		if (mGestureDetector != null) {
			mGestureDetector.onTouchEvent(event);
		}
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN: {
				requestParentDisallowInterceptTouchEvent(true);

				mActivePointerId = event.getPointerId(0);
				mLastTouchX = mActionDownX = getActiveX(event);
				mLastTouchY = mActionDownY = getActiveY(event);

				if (mTranslateUpAnimator != null) {
					mTranslateUpAnimator.cancel();
				}

				mDragDownMatrix.reset();
				mIsDraggingDown = false;
				mIsDragging = false;

				mVelocityTracker = VelocityTracker.obtain();
				if (mVelocityTracker != null) {
					mVelocityTracker.addMovement(event);
				}

				if (mCurrentFlingRunnable != null) {
					mCurrentFlingRunnable.cancelFling();
					mCurrentFlingRunnable = null;
				}
				break;
			}
			case MotionEvent.ACTION_MOVE: {
				float dX = getActiveX(event) - mLastTouchX;
				float dY = getActiveY(event) - mLastTouchY;
				if (!mIsDragging) {
					mIsDragging = Math.sqrt(dX * dX + dY * dY) >= mTouchSlop;
					if (mIsDragging) {
						mLastTouchX = getActiveX(event);
						mLastTouchY = getActiveY(event);
					}
				}

				if (mIsDragging && !mScaleGestureDetector.isInProgress() && event.getPointerCount() == 1) {
					RectF rectF = getDisplayRect();
					if (!mIsDraggingDown && rectF != null) {
						boolean isScrollVertical = Math.abs(getActiveX(event) - mActionDownX) +
								mTouchSlop * 5 < Math.abs(getActiveY(event) - mActionDownY);
						boolean isScrollUp = getActiveY(event) < mActionDownY;
						if (isScrollVertical &&
								(rectF.height() <= getViewHeight() ||
										(Math.round(rectF.top) >= 0 && !isScrollUp) ||
										(Math.round(rectF.bottom) <= getViewHeight() && isScrollUp))) {
							mActionDownX = getActiveX(event);
							mActionDownY = getActiveY(event);
							mIsDraggingDown = true;
						}
					}

					if (mIsDraggingDown) {
						mDragDownMatrix.reset();
						float deltaY = getActiveY(event) - mActionDownY;
						mDragDownMatrix.postTranslate(0, deltaY / 3);
						updateMatrix();
						if (mOnPhotoViewDragListener != null) {
							mOnPhotoViewDragListener.onDragOffset(Math.abs(deltaY / 3), getViewHeight() / 6);
						}
						requestParentDisallowInterceptTouchEvent(true);
					} else {
						mScaleAndDragMatrix.postTranslate(getActiveX(event) - mLastTouchX, getActiveY(event) - mLastTouchY);
						updateMatrix();

						mLastTouchX = getActiveX(event);
						mLastTouchY = getActiveY(event);

						// 处理和ViewPager的滑动冲突
						boolean isScrollHorizontal = Math.abs(getActiveX(event) - mActionDownX) + mTouchSlop
								> Math.abs(getActiveY(event) - mActionDownY) * 5;
						boolean isScrollRight = getActiveX(event) > mActionDownX;

						if (rectF != null) {
							if (isScrollHorizontal && (isScrollRight && Math.round(rectF.left) >= 0)
									|| (!isScrollRight && Math.round(rectF.right) <= getViewWidth())) {
								requestParentDisallowInterceptTouchEvent(false);
							} else {
								requestParentDisallowInterceptTouchEvent(true);
							}
						}
					}
				} else {
					requestParentDisallowInterceptTouchEvent(true);
				}

				if (null != mVelocityTracker) {
					mVelocityTracker.addMovement(event);
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_UP: {
				int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
						>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
				int pointerId = event.getPointerId(pointerIndex);
				if (pointerId == mActivePointerId) {
					int newPointerIndex = pointerIndex == 0 ? 1 : 0;
					mActivePointerId = event.getPointerId(newPointerIndex);
					mActionDownX = mLastTouchX = event.getX(newPointerIndex);
					mActionDownY = mLastTouchY = event.getY(newPointerIndex);
				} else {
					mActionDownX = mLastTouchX = event.getX(mActivePointerIndex);
					mActionDownY = mLastTouchY = event.getY(mActivePointerIndex);
				}
				break;
			}
			case MotionEvent.ACTION_UP: {
				mActivePointerId = INVALID_POINTER_ID;
				if (mIsDragging) {
					if (getMatrixScale() < 1) {
						startZoomAnimation(this, getMatrixScale(), mMinScale, 0, 0);
					}

					// Fling
					if (mVelocityTracker != null) {
						mVelocityTracker.addMovement(event);
						mVelocityTracker.computeCurrentVelocity(1000);
						float vX = mVelocityTracker.getXVelocity();
						float vY = mVelocityTracker.getYVelocity();
						if (Math.abs(vY) > mMinimumVelocity && Math.abs(vY) > Math.abs(vX) && mIsDraggingDown) {
							if (mOnPhotoViewDragListener != null) {
								mOnPhotoViewDragListener.onDragToFinish();
								break;
							}
						}
					}

					float finalDeltaY = getMatrixValue(mDragDownMatrix, Matrix.MTRANS_Y);
					if (Math.abs(finalDeltaY) < getViewHeight() / 6) {
						mTranslateUpAnimator = new TranslateUpAnimator(this, finalDeltaY, 0);
					} else if (mIsDraggingDown) {
						mDragDownMatrix.reset();
						if (mOnPhotoViewDragListener != null) {
							mOnPhotoViewDragListener.onDragToFinish();
						}
					}
				}

				recycleVelocityTracker();
				break;
			}
			case MotionEvent.ACTION_CANCEL: {
				mActivePointerId = INVALID_POINTER_ID;
				// Recycle Velocity Tracker
				recycleVelocityTracker();
				break;
			}
		}

		mActivePointerIndex = event.findPointerIndex(mActivePointerId != INVALID_POINTER_ID ? mActivePointerId : 0);
		return true;
	}

	private void recycleVelocityTracker() {
		if (null != mVelocityTracker) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
	}

	private void updateMatrix() {
		RectF rectF = getDisplayRect();
		if (rectF != null) {
			float deltaX = 0;
			float deltaY = 0;

			if (rectF.width() < getViewWidth()) {
				deltaX = (getViewWidth() - rectF.width()) / 2 - rectF.left;
			} else if (rectF.left > 0) {
				deltaX = -rectF.left;
			} else if (rectF.right < getViewWidth()) {
				deltaX = getViewWidth() - rectF.right;
			}

			if (rectF.height() <= getViewHeight()) {
				deltaY = (getViewHeight() - rectF.height()) / 2 - rectF.top;
			} else if (rectF.top > 0) {
				deltaY = -rectF.top;
			} else if (rectF.bottom < getViewHeight()) {
				deltaY = getViewHeight() - rectF.bottom;
			}

			mScaleAndDragMatrix.postTranslate(deltaX, deltaY);
			Matrix drawMatrix = getDrawMatrix();
			if (mIsDraggingDown) {
				drawMatrix.postConcat(mDragDownMatrix);
			}
			setImageMatrix(drawMatrix);
		}
	}

	private float getActiveY(MotionEvent event) {
		try {
			return event.getY(mActivePointerIndex);
		} catch (Exception e) {
			return event.getY();
		}
	}

	private float getActiveX(MotionEvent event) {
		try {
			return event.getX(mActivePointerIndex);
		} catch (Exception e) {
			return event.getX();
		}
	}

	private void setMatrixScale(float deltaScale, float pointX, float pointY) {
		mScaleAndDragMatrix.setScale(deltaScale, deltaScale, pointX, pointY);
		updateMatrix();
	}

	public void setMatrixTranslateY(float translateY) {
		mDragDownMatrix.reset();
		mDragDownMatrix.postTranslate(0, translateY);
		updateMatrix();
		if (mOnPhotoViewDragListener != null) {
			mOnPhotoViewDragListener.onDragOffset(Math.abs(translateY), getViewHeight() / 6);
		}
	}

	public float getMatrixScale() {
		return (float) Math.sqrt((float) Math.pow(getMatrixValue(mScaleAndDragMatrix, Matrix.MSCALE_X), 2) +
				(float) Math.pow(getMatrixValue(mScaleAndDragMatrix, Matrix.MSKEW_Y), 2));
	}

	private float getMatrixValue(Matrix matrix, int whichValue) {
		matrix.getValues(mMatrixValues);
		return mMatrixValues[whichValue];
	}

	public Matrix getScaleAndDragMatrix() {
		return mScaleAndDragMatrix;
	}

	@Override
	public void setImageDrawable(@Nullable Drawable drawable) {
		super.setImageDrawable(drawable);
		updateBaseMatrix();
		updateMatrix();
	}

	private void requestParentDisallowInterceptTouchEvent(boolean intercept) {
		ViewGroup parent = (ViewGroup) getParent();
		if (parent != null) {
			parent.requestDisallowInterceptTouchEvent(intercept);
		}
	}

	private int getViewHeight() {
		return getHeight() - getPaddingTop() - getPaddingBottom();
	}

	private int getViewWidth() {
		return getWidth() - getPaddingLeft() - getPaddingRight();
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
							   int oldTop, int oldRight, int oldBottom) {
		if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
			mDragDownMatrix.reset();
			updateMatrix();
			setImageMatrix(mBaseMatrix);
		}
	}

	private void updateBaseMatrix() {
		Drawable drawable = getDrawable();
		if (drawable == null) {
			return;
		}
		RectF srcRectF = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		RectF destRecF = new RectF(0, 0, getViewWidth(), getViewHeight());
		mBaseMatrix.setRectToRect(srcRectF, destRecF, Matrix.ScaleToFit.CENTER);
	}

	private RectF getDisplayRect() {
		Matrix matrix = getDrawMatrix();
		Drawable d = getDrawable();
		if (d != null) {
			mDisplayRect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
			// 对mDisplayRect进行变换
			matrix.mapRect(mDisplayRect);
			return mDisplayRect;
		}
		return null;
	}

	private Matrix getDrawMatrix() {
		mDrawMatrix.set(mBaseMatrix);
		mDrawMatrix.postConcat(mScaleAndDragMatrix);
		return mDrawMatrix;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (getDrawable() != null && getDrawable() instanceof BitmapDrawable) {
			if (((BitmapDrawable) getDrawable()).getBitmap() == null ||
					((BitmapDrawable) getDrawable()).getBitmap().isRecycled()) {
				return;
			}
		}
		super.onDraw(canvas);
	}

	private static void startZoomAnimation(DragToFinishPhotoView imageView, final float currentZoom,
										   final float targetZoom, final float focalX, final float focalY) {
		final WeakReference<DragToFinishPhotoView> photoViewWeakReference = new WeakReference<>(imageView);
		ValueAnimator valueAnimator = new ValueAnimator();
		valueAnimator.setFloatValues(currentZoom, targetZoom);
		valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				if (photoViewWeakReference.get() != null) {
					photoViewWeakReference.get().setMatrixScale((Float) animation.getAnimatedValue(),
							focalX, focalY);
				}
			}
		});
		valueAnimator.start();
	}

	private static class TranslateUpAnimator extends ValueAnimator {

		private WeakReference<DragToFinishPhotoView> mPhotoViewWeakReference;

		TranslateUpAnimator(DragToFinishPhotoView imageView, float from, float to) {
			mPhotoViewWeakReference = new WeakReference<>(imageView);
			setFloatValues(from, to);
			addUpdateListener(new AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					if (mPhotoViewWeakReference.get() != null) {
						mPhotoViewWeakReference.get().setMatrixTranslateY((Float) animation.getAnimatedValue());
					}
				}
			});
			start();
		}
	}

	private static class FlingRunnable implements Runnable {

		private static final int SIXTY_FPS_INTERVAL = 1000 / 60;

		// 当我们滚动屏幕内容到达内容边界时，如果再滚动就会有一个发光效果
		private final OverScroller mScroller;
		private int mCurrentX, mCurrentY;
		private WeakReference<DragToFinishPhotoView> mPhotoViewWeakReference;

		FlingRunnable(DragToFinishPhotoView photoView) {
			mScroller = new OverScroller(photoView.getContext());
			mPhotoViewWeakReference = new WeakReference<>(photoView);
		}

		void cancelFling() {
			mScroller.forceFinished(true);
		}

		void fling(int viewWidth, int viewHeight, int velocityX, int velocityY) {
			if (mPhotoViewWeakReference.get() == null) {
				return;
			}

			final RectF rect = mPhotoViewWeakReference.get().getDisplayRect();
			if (rect == null) {
				return;
			}

			final int startX = Math.round(-rect.left);
			final int minX, maxX, minY, maxY;

			if (viewWidth < rect.width()) {
				minX = 0;
				maxX = Math.round(rect.width() - viewWidth);
			} else {
				minX = maxX = startX;
			}

			final int startY = Math.round(-rect.top);
			if (viewHeight < rect.height()) {
				minY = -Math.round(rect.height() - viewHeight);
				maxY = Math.round(rect.height() - viewHeight);
			} else {
				minY = Math.round(rect.height() - viewHeight);
				maxY = 0;
			}

			mCurrentX = startX;
			mCurrentY = startY;

			// If we actually can move, fling the scroller
			if (startX != maxX || startY != maxY) {
				mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0);
			}
		}

		@Override
		public void run() {
			if (mScroller.isFinished() || mPhotoViewWeakReference.get() == null) {
				return; // remaining post that should not be handled
			}

			if (mScroller.computeScrollOffset()) {
				final int newX = mScroller.getCurrX();
				final int newY = mScroller.getCurrY();

				mPhotoViewWeakReference.get().getScaleAndDragMatrix().postTranslate(mCurrentX - newX,
						mCurrentY - newY);
				mPhotoViewWeakReference.get().updateMatrix();

				mCurrentX = newX;
				mCurrentY = newY;

				// Post On animation
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					mPhotoViewWeakReference.get().postOnAnimation(this);
				} else {
					mPhotoViewWeakReference.get().postDelayed(this, SIXTY_FPS_INTERVAL);
				}
			}
		}
	}

	public interface OnPhotoViewDragListener {
		void onDragToFinish();

		void onDragOffset(float offset, float maxOffset);
	}
}
