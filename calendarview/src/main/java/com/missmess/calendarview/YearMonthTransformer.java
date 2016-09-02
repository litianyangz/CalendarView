package com.missmess.calendarview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TypeEvaluator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * YearView and MonthView transition animator helper.
 * use this to implement transition.
 *
 * <p>You should implements animation of all your custom views by your own,
 * using {@link OnTransitListener}. If any view not configured, it will show or hide
 * immediately without animations</p>
 * <p>pls work together with {@link TransitRootView}</p>
 *
 * @author wl
 * @since 2016/08/15 17:52
 */
public final class YearMonthTransformer {
    // delay time leave views to anim pass in or out.
    private final int STAY_DELAY_TIME = 300;
    private final int BASE_TRANSITION_ANIM_DURATION = 300;
    private final int LABEL_SHOWIN_DURATION = 300;
    private final int LABEL_SHOWOUT_DURATION = 300;
    private final int LABEL_ANIM_OFFSET = 100;
    private final float MAX_TRANSIT_FACTOR = 1.3f;
    private final TransitRootView mRootView;
    private final View rootChild1;
    private final View rootChild2;
    private final AnimTransiter mTransiter;
    private YearView mYearView;
    private MonthView mMonthView;
    private final MonthViewObserver monthViewObserver;
    private boolean mvShowMonthTitle; //original month showing status
    private boolean mvShowWeekLabel; //before anim, week showing status
    private OnTransitListener transitListener;
    private boolean animating = false; //indicate transit process
    private final MonthTitleClicker monthTitleClicker;
    private MonthViewPager mMonthViewPager;

    /**
     * transition listener. use this to implement animations of your custom views
     * to show in or show out. You can also make some initialization operation.
     */
    public interface OnTransitListener {
        /**
         * When YearView to MonthView transit process start.
         * use this to animate views to show out in YearView layout.
         * @param transiter animation helpers
         * @param yearView yearView
         * @param monthView monthView
         */
        void onY2MTransitStart(AnimTransiter transiter, YearView yearView, MonthView monthView);
        /**
         * When YearView to MonthView transit process finished.
         * use this to animate views to show in in MonthView layout.
         * @param transiter animation helpers
         * @param yearView yearView
         * @param monthView monthView
         */
        void onY2MTransitEnd(AnimTransiter transiter, YearView yearView, MonthView monthView);
        /**
         * When MonthView to YearView transit process start.
         * use this to animate views to show out in MonthView layout.
         * @param transiter animation helpers
         * @param yearView yearView
         * @param monthView monthView
         */
        void onM2YTransitStart(AnimTransiter transiter, YearView yearView, MonthView monthView);
        /**
         * When MonthView to YearView transit process finished.
         * use this to animate views to show in in YearView layout.
         * @param transiter animation helpers
         * @param yearView yearView
         * @param monthView monthView
         */
        void onM2YTransitEnd(AnimTransiter transiter, YearView yearView, MonthView monthView);
    }

    public YearMonthTransformer(TransitRootView transitRootView, YearView yearView, MonthView monthView) {
        this.mRootView = transitRootView;
        this.rootChild1 = transitRootView.child1;
        this.rootChild2 = transitRootView.child2;

        monthViewObserver = new MonthViewObserver();
        monthTitleClicker = new MonthTitleClicker();
        mTransiter = new AnimTransiter();

        updateYearView(yearView);
        updateMonthView(monthView);
    }

    /**
     * create a transformer when layout1 use YearView and layout2 use MonthViewPager
     * @param transitRootView TransitRootView
     * @param yearView YearView
     * @param monthViewPager MonthViewPager
     */
    public YearMonthTransformer(TransitRootView transitRootView, YearView yearView, MonthViewPager monthViewPager) {
        this(transitRootView, yearView, monthViewPager.getCurrentChild());
        this.mMonthViewPager = monthViewPager;
        mMonthViewPager.addOnMonthChangeListener(new MonthViewPager.OnMonthChangeListener() {
            @Override
            public void onMonthChanged(MonthViewPager monthViewPager, MonthView previous, MonthView current, MonthView next, CalendarMonth currentMonth, CalendarMonth old) {
                updateMonthView(current);
            }
        });
    }

    /**
     * when your YearView changed, should call this to update
     * @param yearView new YearView
     */
    public void updateYearView(YearView yearView) {
        this.mYearView = yearView;
    }

    /**
     * when your MonthView changed, should call this to update
     * @param monthView new MonthView
     */
    public void updateMonthView(MonthView monthView) {
        this.mMonthView = monthView;
        mMonthView.setOnMonthTitleClickListener(monthTitleClicker);
    }

    /**
     * apply to show MonthView
     * @param month month
     */
    public void applyShow(int month) {
        if (rootChild1.getVisibility() != View.VISIBLE || mYearView.getVisibility() != View.VISIBLE || animating)
            return;

        // init data
        animating = true;
        mvShowMonthTitle = mMonthView.mShowMonthTitle;
        mvShowWeekLabel = mMonthView.mShowWeekLabel;
        if(mMonthViewPager != null) {
            passPropertyY2MVP(mYearView, mMonthViewPager, month);
        } else {
            passPropertyY2M(mYearView, mMonthView, month);
        }

        // not handler any event again
        mRootView.setReceiveEvent(false);
        // start layout but not need to be visible
        rootChild2.setVisibility(View.VISIBLE);
        rootChild2.setAlpha(0);
        // add layout listener
        monthViewObserver.setMonth(month);
        rootChild2.getViewTreeObserver().addOnGlobalLayoutListener(monthViewObserver);
    }

    private void transitShow(int month) {
        MonthView child = (MonthView) mYearView.getChildAt(month - 1);
        // screen position
        int[] fromLocation = new int[2];
        child.getLocationOnScreen(fromLocation);
        int[] parentLocation = new int[2];
        mRootView.getLocationOnScreen(parentLocation);
        int[] toLocation = new int[2];
        mMonthView.getLocationOnScreen(toLocation);
        // label height of MonthView
        int labelHeight = mMonthView.MONTH_HEADER_HEIGHT + mMonthView.WEEK_LABEL_HEIGHT;
        int padding = mMonthView.getPaddingLeft();

        // calculate original position
        int oriL = fromLocation[0] - parentLocation[0];
        int oriT = fromLocation[1] - parentLocation[1];
        // calculate final position
        int finL = toLocation[0] - parentLocation[0] + padding;
        int finT = toLocation[1] - parentLocation[1] + labelHeight;

        MonthView transitView = mRootView.useTransitView();
        passPropertyY2M(mYearView, transitView, month);
        // 1-7
        ObjectAnimator propertyAnim = createMonthPropertyAnimator(child, mMonthView, transitView);
        // 8 LayoutParams
        FrameLayout.LayoutParams oriLps = new FrameLayout.LayoutParams(child.getWidth(), child.getHeight());
        oriLps.setMargins(oriL, oriT, 0, 0);
        FrameLayout.LayoutParams finLps = new FrameLayout.LayoutParams(mMonthView.getWidth() - 2 * padding, mMonthView.getHeight() - labelHeight);
        finLps.setMargins(finL, finT, 0, 0);
        ViewGroup.LayoutParams rubbish = new ViewGroup.LayoutParams(0, 0);
        ObjectAnimator positionAnim = ObjectAnimator.ofObject(transitView, "layoutParams", new LpsEvaluator(oriLps, finLps), rubbish, rubbish);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(propertyAnim, positionAnim);
        int transitDuration = obtainTransitAnimDuration(Math.abs(finT - oriT), child.getHeight());
        animSet.setDuration(transitDuration);
        animSet.setInterpolator(new DecelerateInterpolator(2.5f));
        animSet.addListener(new DelayAnimListener(STAY_DELAY_TIME) {
            @Override
            public void onStart(Animator animator) {
                // transit start
                // we just alpha YearView, other view determined by the user
                mTransiter.setDuration(STAY_DELAY_TIME);
                mTransiter.alphaView(mYearView, false);
                if(transitListener != null) {
                    transitListener.onY2MTransitStart(mTransiter, mYearView, mMonthView);
                }
            }

            @Override
            public void onContinue(Animator animator) {
                rootChild1.setVisibility(View.GONE);
            }

            @Override
            public void onEnd(Animator animator) {
                mRootView.setReceiveEvent(true);
                mRootView.recycleTransitView();
                rootChild2.setAlpha(1);
                animating = false;
                // anim other views
                mTransiter.setDuration(LABEL_SHOWIN_DURATION);
                if (transitListener != null) {
                    transitListener.onY2MTransitEnd(mTransiter, mYearView, mMonthView);
                }
                // anim indicator
                animShowIndicator(mTransiter);
                // anim label
                animShowLabel();
            }
        });
        animSet.start();
    }

    private void animShowIndicator(AnimTransiter transiter) {
        if(mMonthViewPager != null && mMonthViewPager.isShowingIndicator()) {
            transiter.slideInViewHorizontal(mMonthViewPager.indicator_left, true);
            transiter.slideInViewHorizontal(mMonthViewPager.indicator_right, false);
        }
    }

    private void animHideIndicator(AnimTransiter transiter) {
        if(mMonthViewPager != null && mMonthViewPager.isShowingIndicator()) {
            transiter.slideOutViewHorizontal(mMonthViewPager.indicator_left, false);
            transiter.slideOutViewHorizontal(mMonthViewPager.indicator_right, true);
        }
    }

    // show label with anim
    private void animShowLabel() {
        if (!mvShowMonthTitle && !mvShowWeekLabel)
            return;

        ObjectAnimator monthAnim = null;
        ObjectAnimator weekAnim = null;

        if (mvShowMonthTitle) {
            int sMonthOffset = -mMonthView.MONTH_HEADER_HEIGHT;
            // delay start should initial its position
            mMonthView.setMonthLabelOffset(sMonthOffset);
            // 1
            monthAnim = ObjectAnimator.ofInt(mMonthView, "monthLabelOffset", sMonthOffset, 0);
            monthAnim.setDuration(LABEL_SHOWIN_DURATION - LABEL_ANIM_OFFSET);
            monthAnim.setStartDelay(mvShowWeekLabel ? LABEL_ANIM_OFFSET : 0);
        }
        if (mvShowWeekLabel) {
            int sWeekOffset = -2 * mMonthView.WEEK_LABEL_HEIGHT;
            // 2
            weekAnim = ObjectAnimator.ofInt(mMonthView, "weekLabelOffset", sWeekOffset, 0);
            weekAnim.setDuration(LABEL_SHOWIN_DURATION);
        }
        AnimatorSet animSet = new AnimatorSet();
        animSet.setInterpolator(new DecelerateInterpolator(2f));
        if (monthAnim != null && weekAnim != null) {
            animSet.playTogether(weekAnim, monthAnim);
        } else if (monthAnim != null) {
            animSet.play(monthAnim);
        } else {
            animSet.play(weekAnim);
        }
        animSet.start();
    }

    private void animHideLabel() {
        if (!mvShowMonthTitle && !mvShowWeekLabel) {
            // after anim finish, start transit
            mRootView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onTransitHide();
                }
            }, LABEL_SHOWOUT_DURATION);
            return;
        }

        ObjectAnimator monthAnim = null;
        ObjectAnimator weekAnim = null;

        if (mvShowMonthTitle) {
            int sMonthOffset = -mMonthView.MONTH_HEADER_HEIGHT;
            // 1
            monthAnim = ObjectAnimator.ofInt(mMonthView, "monthLabelOffset", 0, sMonthOffset);
            monthAnim.setDuration(LABEL_SHOWOUT_DURATION - 2 * LABEL_ANIM_OFFSET);
        }
        if (mvShowWeekLabel) {
            int sWeekOffset = -2 * mMonthView.WEEK_LABEL_HEIGHT;
            // 2
            weekAnim = ObjectAnimator.ofInt(mMonthView, "weekLabelOffset", 0, sWeekOffset);
            weekAnim.setDuration(LABEL_SHOWOUT_DURATION - LABEL_ANIM_OFFSET);
            weekAnim.setStartDelay(mvShowMonthTitle ? LABEL_ANIM_OFFSET : 0);
        }
        AnimatorSet animSet = new AnimatorSet();
        animSet.setInterpolator(new AccelerateInterpolator(2f));
        if (monthAnim != null && weekAnim != null) {
            animSet.playTogether(weekAnim, monthAnim);
        } else if (monthAnim != null) {
            animSet.play(monthAnim);
        } else {
            animSet.play(weekAnim);
        }
        animSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // recovery MonthView label layout
                mMonthView.setMonthLabelOffset(0);
                mMonthView.setWeekLabelOffset(0);

                onTransitHide();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animSet.start();
    }

    private void onTransitHide() {
        rootChild2.setVisibility(View.GONE);
        transitHide();
    }

    private void transitHide() {
        MonthView child = (MonthView) mYearView.getChildAt(mMonthView.getCurrentMonth().getMonth() - 1);
        // screen position
        int[] fromLocation = new int[2];
        mMonthView.getLocationOnScreen(fromLocation);
        int[] toLocation = new int[2];
        child.getLocationOnScreen(toLocation);
        int[] parentLocation = new int[2];
        mRootView.getLocationOnScreen(parentLocation);
        // label height of MonthView
        int labelHeight = mMonthView.MONTH_HEADER_HEIGHT + mMonthView.WEEK_LABEL_HEIGHT;
        int padding = mMonthView.getPaddingLeft();

        // calculate original position
        int oriL = fromLocation[0] - parentLocation[0] + padding;
        int oriT = fromLocation[1] - parentLocation[1] + labelHeight;
        // calculate final position
        int finL = toLocation[0] - parentLocation[0];
        int finT = toLocation[1] - parentLocation[1];

        MonthView transitView = mRootView.useTransitView();
        passPropertyM2M(mMonthView, transitView);

        // 1-7
        ObjectAnimator animators = createMonthPropertyAnimator(mMonthView, child, transitView);
        // 8 LayoutParams
        FrameLayout.LayoutParams oriLps = new FrameLayout.LayoutParams(mMonthView.getWidth() - 2 * padding, mMonthView.getHeight() - labelHeight);
        oriLps.setMargins(oriL, oriT, 0, 0);
        FrameLayout.LayoutParams finLps = new FrameLayout.LayoutParams(child.getWidth(), child.getHeight());
        finLps.setMargins(finL, finT, 0, 0);
        ViewGroup.LayoutParams rubbish = new ViewGroup.LayoutParams(0, 0);
        ObjectAnimator positionAnim = ObjectAnimator.ofObject(transitView, "layoutParams", new LpsEvaluator(oriLps, finLps), rubbish, rubbish);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(animators, positionAnim);
        int transitDuration = obtainTransitAnimDuration(Math.abs(finT - oriT), child.getHeight());
        animSet.setDuration(transitDuration);
        animSet.setInterpolator(new DecelerateInterpolator());
        animSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                rootChild1.setAlpha(1);
                mTransiter.setDuration(STAY_DELAY_TIME);
                mTransiter.alphaView(mYearView, true);
                if(transitListener != null) {
                    transitListener.onM2YTransitEnd(mTransiter, mYearView, mMonthView);
                }
                mRootView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.setReceiveEvent(true);
                        mRootView.recycleTransitView();

                        animating = false;
                    }
                }, STAY_DELAY_TIME);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animSet.start();
    }

    private int obtainTransitAnimDuration(int transitT, int childHeight) {
        float factor = (float) transitT / (float) childHeight / 2f;
        if(factor < 1)
            factor = 1;
        if(factor > MAX_TRANSIT_FACTOR)
            factor = MAX_TRANSIT_FACTOR;
        return (int) (factor * BASE_TRANSITION_ANIM_DURATION);
    }

    /**
     * apply hide OP to MonthView
     *
     * @return false - not necessary to hide; true - attempt to hide
     */
    public boolean applyHide() {
        if (rootChild2.getVisibility() != View.VISIBLE || mMonthView.getVisibility() != View.VISIBLE || animating) {
            // not necessary to hide
            return false;
        }

        // init data
        animating = true;
        mvShowMonthTitle = mMonthView.mShowMonthTitle;
        mvShowWeekLabel = mMonthView.mShowWeekLabel;
        if(mMonthViewPager != null) {
            passPropertyMVP2Y(mMonthViewPager, mYearView);
        } else {
            passPropertyM2Y(mMonthView, mYearView);
        }

        // not handler click event again
        mRootView.setReceiveEvent(false);
        // necessary to be visible
        rootChild1.setVisibility(View.VISIBLE);
        rootChild1.setAlpha(0);
        // clear selection
        mMonthView.clearSelection();
        // add layout listener
        mMonthView.getViewTreeObserver().addOnGlobalLayoutListener(monthViewObserver);
        return true;
    }

    // pass property of YearView to MonthViewPager
    private void passPropertyY2MVP(YearView yearView, MonthViewPager monthViewPager, int month) {
        monthViewPager.setToday(yearView.today);
        monthViewPager.setCurrentMonth(new CalendarMonth(yearView.getYear(), month));
        monthViewPager.setDecors(yearView.getDecors());
    }

    // pass property of YearView to MonthView
    private void passPropertyY2M(YearView yearView, MonthView monthView, int month) {
        monthView.setToday(yearView.today);
        monthView.setYearAndMonth(yearView.getYear(), month);
        monthView.setDecors(yearView.getDecors());
    }

    // pass property of YearView to MonthView
    private void passPropertyM2M(MonthView start, MonthView end) {
        end.setToday(start.today);
        CalendarMonth calendarMonth = start.getCurrentMonth();
        end.setYearAndMonth(calendarMonth.getYear(), calendarMonth.getMonth());
        end.setDecors(start.getDecors());
    }

    // pass property of MonthView to YearView
    private void passPropertyM2Y(MonthView monthView, YearView yearView) {
        yearView.setYear(monthView.getCurrentMonth().getYear());
    }

    // pass property of MonthViewPager to YearView
    private void passPropertyMVP2Y(MonthViewPager monthViewPager, YearView yearView) {
        yearView.setYear(monthViewPager.getCurrentChild().getCurrentMonth().getYear());
        yearView.setDecors(monthViewPager.getDecors());
        yearView.setToday(monthViewPager.getCurrentChild().today);
    }

    private ObjectAnimator createMonthPropertyAnimator(MonthView start, MonthView end, MonthView target) {
        // animators
        ArgbEvaluator argbEvaluator = new ArgbEvaluator();
        // 7 properties
        int property1 = start.normalDayTextColor;
        PropertyValuesHolder pvh1 = PropertyValuesHolder.ofObject("normalDayTextColor", argbEvaluator, property1, end.normalDayTextColor);
        int property2 = start.normalDayTextSize;
        PropertyValuesHolder pvh2 = PropertyValuesHolder.ofInt("normalDayTextSize", property2, end.normalDayTextSize);
        int property3 = start.dayCircleRadius;
        PropertyValuesHolder pvh3 = PropertyValuesHolder.ofInt("dayCircleRadius", property3, end.dayCircleRadius);
        int property4 = start.dayRowHeight;
        PropertyValuesHolder pvh4 = PropertyValuesHolder.ofInt("dayRowHeight", property4, end.dayRowHeight);
        int property5 = start.circleTextColor;
        PropertyValuesHolder pvh5 = PropertyValuesHolder.ofObject("circleTextColor", argbEvaluator, property5, end.circleTextColor);
        int property6 = start.todayCircleBgColor;
        PropertyValuesHolder pvh6 = PropertyValuesHolder.ofObject("todayCircleBgColor", argbEvaluator, property6, end.todayCircleBgColor);
        int property7 = start.todayTextColor;
        PropertyValuesHolder pvh7 = PropertyValuesHolder.ofObject("todayTextColor", argbEvaluator, property7, end.todayTextColor);
        return ObjectAnimator.ofPropertyValuesHolder(target, pvh1, pvh2, pvh3, pvh4, pvh5, pvh6, pvh7);
    }

    public void setOnTransitListener(OnTransitListener listener) {
        transitListener = listener;
    }

    class MonthViewObserver implements ViewTreeObserver.OnGlobalLayoutListener {
        private int month;

        public void setMonth(int month) {
            this.month = month;
        }

        @Override
        public void onGlobalLayout() {
            if (rootChild2.getAlpha() == 0) {
                transitShow(month);
            } else {
                // anim other view
                // increase 50 to ensure animation finish
                mTransiter.setDuration(LABEL_SHOWOUT_DURATION + 50);
                if(transitListener != null) {
                    transitListener.onM2YTransitStart(mTransiter, mYearView, mMonthView);
                }
                // anim indicator
                animHideIndicator(mTransiter);
                // anim label
                animHideLabel();
            }
            rootChild2.getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    }

    class MonthTitleClicker implements MonthView.OnMonthTitleClickListener {
        @Override
        public void onMonthClick(MonthView monthView, CalendarMonth calendarMonth) {
            applyHide();
        }
    }
    
    class LpsEvaluator implements TypeEvaluator<ViewGroup.LayoutParams> {
        private FrameLayout.LayoutParams start;
        private FrameLayout.LayoutParams end;
        private FrameLayout.LayoutParams lps;

        public LpsEvaluator(FrameLayout.LayoutParams start, FrameLayout.LayoutParams end) {
            this.start = start;
            this.end = end;
            lps = new FrameLayout.LayoutParams(0, 0);
        }

        @Override
        public ViewGroup.LayoutParams evaluate(float t, ViewGroup.LayoutParams startValue, ViewGroup.LayoutParams endValue) {
            float width = (float)start.width + ((float)end.width - (float)start.width) * t;
            float height = (float)start.height + ((float)end.height - (float)start.height) * t;
            float leftMargin = (float)start.leftMargin + ((float)end.leftMargin - (float)start.leftMargin) * t;
            float topMargin = (float)start.topMargin + ((float)end.topMargin - (float)start.topMargin) * t;

//            FrameLayout.LayoutParams newLps = new FrameLayout.LayoutParams((int)width, (int)height);
//            newLps.setMargins((int)leftMargin, (int)topMargin, 0, 0);
            lps.width = (int) width;
            lps.height = (int) height;
            lps.leftMargin = (int) leftMargin;
            lps.topMargin = (int) topMargin;
            return lps;
        }
    }
}
