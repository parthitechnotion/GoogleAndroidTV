/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.usbtuner;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A customized GuidedStepFragment for setup UI.
 */
public abstract class SetupGuidedStepFragment extends GuidedStepFragment {

    // Used from {@link GuidedStepFragment#getCurrentGuidedStepFragment}.
    // String from {@link GuidedStepFragment}
    private static final String FRAGMENT_TAG = "leanBackGuidedStepFragment";

    public static void addSetupFragment(FragmentManager fm, SetupGuidedStepFragment f,
            boolean isTop) {
        int id = android.R.id.content;

        FragmentTransaction ft = fm.beginTransaction();
        if (!isTop) {
            ft.addToBackStack(null);
        }
        ft.replace(id, f, FRAGMENT_TAG).commit();
    }

    // Create Animator without target View.
    private static class UntargetableAnimatorSet extends Animator {

        private final AnimatorSet mAnimatorSet;

        UntargetableAnimatorSet(AnimatorSet animatorSet) {
            mAnimatorSet = animatorSet;
        }

        @Override
        public void addListener(Animator.AnimatorListener listener) {
            mAnimatorSet.addListener(listener);
        }

        @Override
        public void cancel() {
            mAnimatorSet.cancel();
        }

        @Override
        public Animator clone() {
            return mAnimatorSet.clone();
        }

        @Override
        public void end() {
            mAnimatorSet.end();
        }

        @Override
        public long getDuration() {
            return mAnimatorSet.getDuration();
        }

        @Override
        public ArrayList<Animator.AnimatorListener> getListeners() {
            return mAnimatorSet.getListeners();
        }

        @Override
        public long getStartDelay() {
            return mAnimatorSet.getStartDelay();
        }

        @Override
        public boolean isRunning() {
            return mAnimatorSet.isRunning();
        }

        @Override
        public boolean isStarted() {
            return mAnimatorSet.isStarted();
        }

        @Override
        public void removeAllListeners() {
            mAnimatorSet.removeAllListeners();
        }

        @Override
        public void removeListener(Animator.AnimatorListener listener) {
            mAnimatorSet.removeListener(listener);
        }

        @Override
        public Animator setDuration(long duration) {
            return mAnimatorSet.setDuration(duration);
        }

        @Override
        public void setInterpolator(TimeInterpolator value) {
            mAnimatorSet.setInterpolator(value);
        }

        @Override
        public void setStartDelay(long startDelay) {
            mAnimatorSet.setStartDelay(startDelay);
        }

        @Override
        public void setTarget(Object target) {
            // ignore
        }

        @Override
        public void setupEndValues() {
            mAnimatorSet.setupEndValues();
        }

        @Override
        public void setupStartValues() {
            mAnimatorSet.setupStartValues();
        }

        @Override
        public void start() {
            mAnimatorSet.start();
        }
    }

    private static Animator createDummyAnimator(List<Animator> animators) {
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animators);
        return new UntargetableAnimatorSet(animatorSet);
    }

    private static Animator createActionAnimator(final View v, List<Animator> animators) {
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(animators);
        animatorSet.setTarget(v);
        return animatorSet;
    }

    private static Animator createAnimator(View v, int resId) {
        Animator animator = AnimatorInflater.loadAnimator(v.getContext(), resId);
        animator.setTarget(v);
        return animator;
    }

    private static void addActionsAnimator(VerticalGridView view, int beforeId, int resId,
            int delay, @NonNull List<Animator> animators) {
        if (view != null) {
            int count = 0;
            int childCount = view.getAdapter().getItemCount();
            Context ctx = view.getContext();

            // Enumerate visible actions item and construct animations for them.
            for (int i = 0; i < childCount; ++i) {
                RecyclerView.ViewHolder viewHolder = view.findViewHolderForPosition(i);
                if( viewHolder!= null) {
                    ArrayList<Animator> childAnimators = new ArrayList<>();

                    View childView = viewHolder.itemView;
                    Animator animator;
                    animator = AnimatorInflater.loadAnimator(ctx, beforeId);
                    animator.setDuration(count * delay);
                    childAnimators.add(animator);

                    animator = AnimatorInflater.loadAnimator(ctx, resId);
                    childAnimators.add(animator);

                    Animator lastAnimator = createActionAnimator(childView, childAnimators);
                    animators.add(lastAnimator);

                    count++;
                }
            }
        }
    }

    private static class VerticalSpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int mVerticalSpaceHeight;

        public VerticalSpaceItemDecoration(int mVerticalSpaceHeight) {
            this.mVerticalSpaceHeight = mVerticalSpaceHeight;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            if (parent.getChildAdapterPosition(view) != parent.getAdapter().getItemCount() - 1) {
                outRect.bottom = mVerticalSpaceHeight;
            }
        }
    }

    private class SetupGuidedActionsStylist extends GuidedActionsStylist {

        @Override
        public View onCreateView(LayoutInflater inflater, final ViewGroup container) {
            View view = super.onCreateView(inflater, container);

            // Change default scroll behaviour of the actions view.
            mActionsGridView.addItemDecoration(new VerticalSpaceItemDecoration(view.getContext()
                    .getResources().getInteger(R.integer.ut_guidedactions_vertical_spcae)));
            mActionsGridView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED);
            mActionsGridView.setWindowAlignmentOffsetPercent(
                    VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            mActionsGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
            return view;
        }

        @Override
        public int onProvideLayoutId() {
            return R.layout.ut_guidedactions;
        }

        public void onSetupFragmentEnter(@NonNull List<Animator> animators) {
            addActionsAnimator(mActionsGridView, R.animator.setup_before_entry,
                    R.animator.setup_entry, 50, animators);
        }

        public void onSetupFragmentExit(@NonNull List<Animator> animators) {
            addActionsAnimator(mActionsGridView, R.animator.setup_before_exit,
                    R.animator.setup_exit, 50, animators);
        }
    }

    private class SetupGuidanceStylist extends GuidanceStylist {
        @Override
        public int onProvideLayoutId() {
            return R.layout.ut_guidance;
        }

        public void onSetupFragmentEnter(@NonNull List<Animator> animators) {
            animators.add(createAnimator(getTitleView(), R.animator.setup_entry));
            animators.add(createAnimator(getDescriptionView(), R.animator.setup_entry));
            animators.add(createAnimator(getBreadcrumbView(), R.animator.setup_entry));
        }

        public void onSetupFragmentExit(@NonNull List<Animator> animators) {
            animators.add(createAnimator(getTitleView(), R.animator.setup_exit));
            animators.add(createAnimator(getDescriptionView(), R.animator.setup_exit));
            animators.add(createAnimator(getBreadcrumbView(), R.animator.setup_exit));
        }
    }

    public void addDefaultAction(List<GuidedAction> actions, long id, String title) {
        GuidedAction.Builder builder = new GuidedAction.Builder()
                .id(id)
                .title(title);
        actions.add(builder.build());
    }

    public void addIntentAction(List<GuidedAction> actions, long id, Class cls, String title) {
        GuidedAction.Builder builder = new GuidedAction.Builder()
                .id(id)
                .intent(new Intent(getActivity(), cls))
                .title(title);
        actions.add(builder.build());
    }

    public void addBooleanSetupAction(List<GuidedAction> actions, long id, boolean value,
            int titleRes) {
        GuidedAction.Builder builder = new GuidedAction.Builder()
                .id(id)
                .title(getString(titleRes))
                .description(getString(value ? R.string.ut_setup_on : R.string.ut_setup_off));
        actions.add(builder.build());
    }

    public void addCheckedAction(List<GuidedAction> actions, long id, Class cls, String title,
            boolean checked) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .intent(new Intent(getActivity(), cls))
                .title(title)
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(checked)
                .build());
    }

    public void updateGuidanceTitle(String title) {
        GuidanceStylist stylist = getGuidanceStylist();
        if (stylist != null) {
            TextView view = stylist.getTitleView();
            if (view != null) {
                view.setText(title);
            }
        }
    }

    public void updateGuidanceDescription(String description) {
        GuidanceStylist stylist = getGuidanceStylist();
        if (stylist != null) {
            TextView view = stylist.getDescriptionView();
            if (view != null) {
                view.setText(description);
            }
        }
    }

    public void updateGuidanceBreadcrumb(String breadcrumb) {
        GuidanceStylist stylist = getGuidanceStylist();
        if (stylist != null) {
            TextView view = stylist.getBreadcrumbView();
            if (view != null) {
                view.setText(breadcrumb);
            }
        }
    }

    @Override
    protected void onProvideFragmentTransitions() {
        // Disable fragment transition animations.
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Override {@link RelativeLayout#LayoutParams} for {@link GuidedStepFragment}
        View view = super.onCreateView(inflater, container, savedInstanceState);
        View frameView = view.findViewById(R.id.content_frame);
        if (frameView instanceof ViewGroup) {
            ((ViewGroup) frameView).setClipChildren(false);
            ((ViewGroup) frameView).setClipToPadding(false);
            frameView.setBackgroundColor(
                    view.getContext().getResources().getColor(R.color.ut_guidedstep_background));
        }
        View contentView = view.findViewById(R.id.content_fragment);
        if (contentView instanceof ViewGroup) {
            ((ViewGroup) contentView).setClipChildren(false);
            ((ViewGroup) contentView).setClipToPadding(false);
            RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) contentView.getLayoutParams();
            layoutParams.width = view.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.ut_guidance_section_width);
            layoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            contentView.setLayoutParams(layoutParams);
            contentView.setPadding(0, 0, 0, 0);
        }
        View actionView = view.findViewById(R.id.action_fragment);
        if (actionView instanceof ViewGroup) {
            ((ViewGroup) actionView).setClipChildren(false);
            ((ViewGroup) actionView).setClipToPadding(false);
            RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) actionView.getLayoutParams();
            layoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            actionView.setLayoutParams(layoutParams);
            actionView.setPadding(0, 0, 0, 0);
        }
        return view;
    }

    protected void doStartAnimation(View mainView, GuidanceStylist guidanceStylist,
            GuidedActionsStylist actionsStylist) {
        ArrayList<Animator> animators = new ArrayList<>();
        if (guidanceStylist instanceof SetupGuidanceStylist) {
            ((SetupGuidanceStylist)guidanceStylist).onSetupFragmentEnter(animators);
        }
        if (actionsStylist instanceof SetupGuidedActionsStylist) {
            ((SetupGuidedActionsStylist)actionsStylist).onSetupFragmentEnter(animators);
        }
        if (animators.size() > 0) {
            Animator anim = createDummyAnimator(animators);
            anim.setTarget(mainView);
            setHWLayerAnimListenerIfAlpha(mainView, anim);

            anim.start();
        }
    }

    protected void setStartAnimation() {
        final GuidedActionsStylist actionsStylist = getGuidedActionsStylist();
        final GuidanceStylist guidanceStylist = getGuidanceStylist();
        final VerticalGridView actionsView = actionsStylist.getActionsGridView();
        final View mainView = getView();
        if (actionsView != null) {
            actionsView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            actionsView.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            doStartAnimation(mainView, guidanceStylist, actionsStylist);
                        }
                    });
        }
    }

    protected void doExitAnimation(final Runnable onAnimationEnded) {
        ArrayList<Animator> animators = new ArrayList<>();
        final View mainView = getView();
        GuidanceStylist guidanceStylist = getGuidanceStylist();
        GuidedActionsStylist actionsStylist = getGuidedActionsStylist();
        if (guidanceStylist instanceof SetupGuidanceStylist) {
            ((SetupGuidanceStylist)guidanceStylist).onSetupFragmentExit(animators);
        }
        if (actionsStylist instanceof SetupGuidedActionsStylist) {
            ((SetupGuidedActionsStylist)actionsStylist).onSetupFragmentExit(animators);
        }
        if (animators.size() > 0) {
            Animator anim = createDummyAnimator(animators);
            anim.setTarget(mainView);
            setHWLayerAnimListenerIfAlpha(mainView, anim);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onAnimationEnded.run();
                }
            });
            anim.start();
        }
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return (GuidanceStylist)new SetupGuidanceStylist();
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return (GuidedActionsStylist) new SetupGuidedActionsStylist();
    }

    // Code from {@link FragmentManager#FragmentManagerImpl}
    private static class AnimateOnHWLayerIfNeededListener implements Animator.AnimatorListener {
        private boolean mShouldRunOnHWLayer = false;
        private View mView;
        public AnimateOnHWLayerIfNeededListener(final View v) {
            if (v == null) {
                return;
            }
            mView = v;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mShouldRunOnHWLayer = shouldRunOnHWLayer(mView, animation);
            if (mShouldRunOnHWLayer) {
                mView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mShouldRunOnHWLayer) {
                mView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            mView = null;
            animation.removeListener(this);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    private static boolean modifiesAlpha(Animator anim) {
        if (anim == null) {
            return false;
        }
        if (anim instanceof ValueAnimator) {
            ValueAnimator valueAnim = (ValueAnimator) anim;
            PropertyValuesHolder[] values = valueAnim.getValues();
            for (PropertyValuesHolder value : values) {
                if (("alpha").equals(value.getPropertyName())) {
                    return true;
                }
            }
        } else if (anim instanceof AnimatorSet) {
            List<Animator> animList = ((AnimatorSet) anim).getChildAnimations();
            for (Animator animator : animList) {
                if (modifiesAlpha(animator)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldRunOnHWLayer(View v, Animator anim) {
        if (v == null || anim == null) {
            return false;
        }
        return v.getLayerType() == View.LAYER_TYPE_NONE
                && v.hasOverlappingRendering()
                && modifiesAlpha(anim);
    }

    private void setHWLayerAnimListenerIfAlpha(final View v, Animator anim) {
        if (v == null || anim == null) {
            return;
        }
        if (shouldRunOnHWLayer(v, anim)) {
            anim.addListener(new AnimateOnHWLayerIfNeededListener(v));
        }
    }
}
