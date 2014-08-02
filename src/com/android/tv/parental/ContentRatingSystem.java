/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.parental;

import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class ContentRatingSystem {
    private final static String TAG = "ContentRatingSystem";

    // Id of this content rating system. It should be unique in an XML file.
    private String mId;

    // Domain of this content rating system. It's package name now.
    private String mDomain;

    // Display name of this content rating system. (e.g. TV-PG)
    private String mDisplayName;

    // Description of this content rating system.
    private String mDescription;

    // Country code of this content rating system.
    private String mCountry;

    // Ordered list of main content ratings. UX should respect the order.
    private List<Rating> mRatings;

    // Ordered list of sub content ratings. UX should respect the order.
    private List<SubRating> mSubRatings;

    // List of orders. This describes the automatic lock/unlock relationship between ratings.
    // For example, let say we have following order.
    //    <order>
    //        <rating id="US_TVPG_Y" />
    //        <rating id="US_TVPG_Y7" />
    //    </order>
    // This means that locking US_TVPG_Y7 automatically locks US_TVPG_Y and
    // unlocking US_TVPG_Y automatically unlocks US_TVPG_Y7 from the UX.
    // An user can still unlock US_TVPG_Y while US_TVPG_Y7 is locked by manually.
    private List<Order> mOrders;

    public String getId(){
        return mId;
    }

    public String getDomain() {
        return mDomain;
    }

    public String getDisplayName(){
        return mDisplayName;
    }

    public String getDescription(){
        return mDescription;
    }

    public String getCountry(){
        return mCountry;
    }

    public List<Rating> getRatings(){
        return mRatings;
    }

    public List<SubRating> getSubRatings(){
        return mSubRatings;
    }

    public List<Order> getOrders(){
        return mOrders;
    }

    private ContentRatingSystem(
            String id, String domain, String displayName, String description, String country,
            List<Rating> ratings, List<SubRating> subRatings, List<Order> orders) {
        mId = id;
        mDomain = domain;
        mDisplayName = displayName;
        mDescription = description;
        mCountry = country;
        mRatings = ratings;
        mSubRatings = subRatings;
        mOrders = orders;
    }

    public static class Builder {
        private String mId;
        private String mDomain;
        private String mDisplayName;
        private String mDescription;
        private String mCountry;
        private List<Rating.Builder> mRatingBuilders = new ArrayList<Rating.Builder>();
        private List<SubRating.Builder> mSubRatingBuilders = new ArrayList<SubRating.Builder>();
        private List<Order.Builder> mOrderBuilders = new ArrayList<Order.Builder>();

        public void setId(String id) {
            mId = id;
        }

        public void setDomain(String domain) {
            mDomain = domain;
        }

        public void setDisplayName(String displayName) {
            mDisplayName = displayName;
        }

        public void setDescription(String description) {
            mDescription = description;
        }

        public void setCountry(String country) {
            mCountry = country;
        }

        public void addRatingBuilder(Rating.Builder ratingBuilder) {
            // To provide easy access to the SubRatings in it,
            // Rating has reference to SubRating, not Id of it.
            // (Note that Rating/SubRating is ordered list so we cannot use Map)
            // To do so, we need to have list of all SubRatings which might not be available
            // at this moment. Keep builders here and build it with SubRatings later.
            mRatingBuilders.add(ratingBuilder);
        }

        public void addSubRatingBuilder(SubRating.Builder subRatingBuilder) {
            // SubRatings would be built rather to keep consistency with other fields.
            mSubRatingBuilders.add(subRatingBuilder);
        }

        public void addOrderBuilder(Order.Builder orderBuilder) {
            // To provide easy access to the Ratings in it,
            // Order has reference to Rating, not Id of it.
            // (Note that Rating/SubRating is ordered list so we cannot use Map)
            // To do so, we need to have list of all Rating which might not be available
            // at this moment. Keep builders here and build it with Ratings later.
            mOrderBuilders.add(orderBuilder);
        }

        public ContentRatingSystem build() {
            if (TextUtils.isEmpty(mId)) {
                throw new IllegalArgumentException("Id cannot be empty");
            }
            if (TextUtils.isEmpty(mDomain)) {
                throw new IllegalArgumentException("Domain cannot be empty");
            }

            List<SubRating> subRatings = new ArrayList<SubRating>();
            if (mSubRatingBuilders != null) {
                for (SubRating.Builder builder : mSubRatingBuilders) {
                    subRatings.add(builder.build());
                }
            }

            if (mRatingBuilders.size() <= 0) {
                throw new IllegalArgumentException("Rating isn't available.");
            }
            List<Rating> ratings = new ArrayList<Rating>();
            if (mRatingBuilders != null) {
                // Map string ID to object.
                for (Rating.Builder builder : mRatingBuilders) {
                    ratings.add(builder.build(subRatings));
                }

                // Sanity check.
                for (SubRating subRating : subRatings) {
                    boolean used = false;
                    for (Rating rating : ratings) {
                        if (rating.getSubRatings().contains(subRating)) {
                            used = true;
                            break;
                        }
                    }
                    if (!used) {
                        throw new IllegalArgumentException("Subrating " + subRating.getId() +
                            " isn't used by any rating");
                    }
                }
            }

            List<Order> orders = new ArrayList<Order>();
            if (mOrderBuilders != null) {
                for (Order.Builder builder : mOrderBuilders) {
                    orders.add(builder.build(ratings));
                }
            }

            return new ContentRatingSystem(mId, mDomain, mDisplayName, mDescription, mCountry,
                    ratings, subRatings, orders);
        }
    }

    public static class Rating {
        private String mId;
        private String mDisplayName;
        private String mDescription;
        private Uri mIconUri;
        private int mAgeHint;
        private List<SubRating> mSubRatings;

        public String getId() {
            return mId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getDescription() {
            return mDescription;
        }

        public Uri getIconUri() {
            return mIconUri;
        }

        public int getAgeHint() {
            return mAgeHint;
        }

        public List<SubRating> getSubRatings() {
            return mSubRatings;
        }

        private Rating(String id, String displayName, String description, Uri iconUri,
                int ageHint, List<SubRating> subRatings) {
            mId = id;
            mDisplayName = displayName;
            mDescription = description;
            mIconUri = iconUri;
            mAgeHint = ageHint;
            mSubRatings = subRatings;
        }

        public static class Builder {
            private String mId;
            private String mDisplayName;
            private String mDescription;
            private Uri mIconUri;
            private Integer mAgeHint;
            private List<String> mSubRatingIds = new ArrayList<String>();

            public Builder() {
            }

            public void setId(String id) {
                mId = id;
            }

            public void setDisplayName(String displayName) {
                mDisplayName = displayName;
            }

            public void setDescription(String description) {
                mDescription = description;
            }

            public void setIconUri(Uri iconUri) {
                mIconUri = iconUri;
            }

            public void setAgeHint(int ageHint) {
                mAgeHint = (mAgeHint == null) ? new Integer(ageHint) : (Integer) ageHint;
            }

            public void addSubRatingId(String subRatingId) {
                mSubRatingIds.add(subRatingId);
            }

            private Rating build(List<SubRating> allDefinedSubRatings) {
                if (TextUtils.isEmpty(mId)) {
                    throw new IllegalArgumentException("A rating should have non-empty id");
                }
                if (allDefinedSubRatings == null && mSubRatingIds.size() > 0) {
                    throw new IllegalArgumentException("Invalid subrating for rating " +
                            mId);
                }
                if (mAgeHint == null || mAgeHint < 0) {
                    throw new IllegalArgumentException("Rating " + mId + " should define " +
                        "non-negative ageHint");
                }

                List<SubRating> subRatings = new ArrayList<SubRating>();
                for (String subRatingId : mSubRatingIds) {
                    boolean found = false;
                    for (SubRating subRating : allDefinedSubRatings) {
                        if (subRatingId.equals(subRating.getId())) {
                            found = true;
                            subRatings.add(subRating);
                            break;
                        }
                    }
                    if (!found) {
                        throw new IllegalArgumentException("Unknown subrating Id " + subRatingId +
                                " in rating " + mId);
                    }
                }
                return new Rating(
                        mId, mDisplayName, mDescription, mIconUri, (int) mAgeHint, subRatings);
            }
        }
    }

    public static class SubRating {
        private String mId;
        private String mDisplayName;
        private String mDescription;
        private Uri mIconUri;

        public String getId() {
            return mId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getDescription() {
            return mDescription;
        }

        public Uri getIconUri() {
            return mIconUri;
        }

        private SubRating(String id, String displayName, String description, Uri iconUri) {
            mId = id;
            mDisplayName = displayName;
            mDescription = description;
            mIconUri = iconUri;
        }

        public static class Builder {
            private String mId;
            private String mDisplayName;
            private String mDescription;
            private Uri mIconUri;

            public Builder() {
            }

            public void setId(String id) {
                mId = id;
            }

            public void setDisplayName(String displayName) {
                mDisplayName = displayName;
            }

            public void setDescription(String description) {
                mDescription = description;
            }

            public void setIconUri(Uri iconUri) {
                mIconUri = iconUri;
            }

            private SubRating build() {
                if (TextUtils.isEmpty(mId)) {
                    throw new IllegalArgumentException("A subrating should have non-empty id");
                }
                return new SubRating(mId, mDisplayName, mDescription, mIconUri);
            }
        }
    }

    public static class Order {
        private List<Rating> mRatingOrder;

        public List<Rating> getRatingOrder() {
            return mRatingOrder;
        }

        private Order(List<Rating> ratingOrder) {
            mRatingOrder = ratingOrder;
        }

        public static class Builder {
            private final List<String> mRatingIds = new ArrayList<String>();

            public Builder() {
            }

            private Order build(List<Rating> ratings) {
                List<Rating> ratingOrder = new ArrayList<Rating>();
                for (String ratingId : mRatingIds) {
                    boolean found = false;
                    for (Rating rating : ratings) {
                        if (ratingId.equals(rating.getId())) {
                            found = true;
                            ratingOrder.add(rating);
                            break;
                        }
                    }

                    if (!found) {
                        throw new IllegalArgumentException("Unknown rating " + ratingId +
                                " in order tag");
                    }
                }

                return new Order(ratingOrder);
            }

            public void addRatingId(String ratingId) {
                mRatingIds.add(ratingId);
            }
        }
    }
}
