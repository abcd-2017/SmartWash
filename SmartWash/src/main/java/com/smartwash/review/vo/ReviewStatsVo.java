package com.smartwash.review.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 复习统计视图：近 30 天每日复习数 + 各评分占比。
 */
@Data
public class ReviewStatsVo {

    /** 近 30 天每日复习数（缺日补零） */
    private List<DailyCount> daily;

    private List<RatingShare> ratingShare;

    @Data
    public static class DailyCount {

        private LocalDate date;

        private long count;

        public DailyCount() {
        }

        public DailyCount(LocalDate date, long count) {
            this.date = date;
            this.count = count;
        }
    }

    @Data
    public static class RatingShare {

        /** 1=Again 2=Hard 3=Good 4=Easy */
        private Integer rating;

        private long count;

        public RatingShare() {
        }

        public RatingShare(Integer rating, long count) {
            this.rating = rating;
            this.count = count;
        }
    }
}
