package com.frever.platform.timers.utils;

public interface RedshiftQueries {

    /**
     * https://friendfactory.atlassian.net/wiki/spaces/FFTS/pages/1854570497/Introduce+Ranking+of+Templates+2024-10-22
     */
    String GET_TEMPLATE_RANKINGS = """
        --first define some help parameters
        with parameters as (
        select
            7::float as exponential_decay_factor,
            current_timestamp::timestamp as session_start,
            date(session_start)::date as session_start_date,
            1::int as minimum_template_usage,
            5::int as conversion_rate_smoothing_factor,
            0.4::float as total_usage_weight,
            0.6::float as conversion_rate_weight
        ),
        
        --when calculating the conversion rate, we only want to count each users' first view of a video in most feeds, however not in the Template and Remix feeds
        first_view as (
        select
            groupid_watched as groupid,
            videoid,
            min("timestamp") as ts
        from core.facts_views
        where coalesce(feedtype,'NoInfo') not in ('Based on Template', 'Remix')
        group by 1,2
        ),
        
        --aggregate views by date and video
        video_views as (
        select
            videoid,
            date(ts)::date as date,
            count(*) as views
        from first_view v
        join parameters p on p.session_start > v.ts
        group by 1,2
        ),
        
        --find the combo of templates and videos
        videos_and_templates as (
        select
            id::bigint as videoid,
            maintemplateid::bigint as templateid,
            date(createdtime)::date as date,
            row_number() over (partition by maintemplateid order by id) as video_template_order
        from core.facts_videos v
        join parameters p on p.session_start > v.createdtime
        where maintemplateid is not null
        ),
        
        --aggregate views by date and template
        template_views as (
        select
            templateid,
            vv.date as videowatcheddate,
            sum(views) as template_views
        from video_views vv
        join videos_and_templates vt using(videoid)
        group by 1,2
        ),
        
        --aggregate usage of templates by date
        template_usage as (
        select
            templateid,
            t.date as videocreateddate,
            count(distinct t.videoid) as template_videos
        from videos_and_templates t
        group by 1,2
        ),
        
        --now we want to weight views and usage
        weighted_usage_and_views as (
        --first weight views
        select
            t.templateid,
            t.videowatcheddate as date,
            'view'::varchar as type,
            template_views as metric,
            sum(template_views) over (partition by t.templateid) as total_usage,
            --decaying views based on the number of days since they happened
            exp(datediff(day, t.videowatcheddate, p.session_start_date) / exponential_decay_factor)::float as weight_factor,
            --weighted views is views / weight factor
            template_views / weight_factor as weighted_value
        from template_views t, parameters p
        union all
        --then weight usage
        select
            t.templateid,
            t.videocreateddate as date,
            'usage'::varchar as type,
            template_videos as metric,
            sum(template_videos) over (partition by t.templateid) as total_usage,
            --decaying template usage based on the number of days since they were created
            exp(datediff(day, t.videocreateddate, p.session_start_date) / exponential_decay_factor)::float as weight_factor,
            --weighted usage is usage / weight factor
            template_videos / weight_factor as weighted_value
        from template_usage t, parameters p
        ),
        
        --summarize weighted views, weighted usage, and thereby conversion rate by template
        aggregate_weighted_usage_and_views as (
        select
            uv.templateid,
            minimum_template_usage,
            sum(case when type = 'view' then weighted_value end) as weighted_views_total,
            sum(case when type = 'usage' then weighted_value end) as weighted_usage_total,
            --conversion rate should never be higher than 1 and should be null if views = 0
            least((weighted_usage_total::float/nullif(weighted_views_total,0)),1) as conversion_rate
        from weighted_usage_and_views uv, parameters p
        group by 1,2
        --this filter says that the weighted usage should be higher than this parameter in order to be considered
        having weighted_usage_total > minimum_template_usage
        ),
        
        --calculate mean and std dev
        stats as (
        select
            avg(weighted_usage_total) as usage_mean,
            stddev(weighted_usage_total) as usage_stddev
        from aggregate_weighted_usage_and_views
        ),
        
        --cap the weighted_usage_total at (mean + 3 * stddev)
        capped_usage as (
        select
            templateid,
            weighted_views_total,
            weighted_usage_total,
            conversion_rate,
            s.usage_mean,
            s.usage_stddev,
            least(weighted_usage_total, usage_mean + 3 * usage_stddev) as capped_usage_total
        from aggregate_weighted_usage_and_views a
        cross join stats s
        ),
        
        --now calculate the combined score
        final_ranking as (
        select
            templateid,
            weighted_views_total,
            weighted_usage_total,
            usage_mean,
            usage_stddev,
            capped_usage_total,
            conversion_rate,
            --templates with very few views and high conversion rate (1 view, 1 usage for example) needs to be smoothed out. this is done here
            (conversion_rate * weighted_views_total / (weighted_views_total + conversion_rate_smoothing_factor)) as smoothed_conversion_rate,
            --in order to combine a rate value with an int, we normalize both usage and conversion rate. here we use the capped_usage_total
            capped_usage_total / max(capped_usage_total) over() as normalized_usage,
            smoothed_conversion_rate / max(smoothed_conversion_rate) over() as normalized_conversion_rate,
            --here's the weights for each of the metrics
            conversion_rate_weight,
            total_usage_weight
        from capped_usage, parameters p
        ),
        
        --calculate the combine score
        combine_scores as (
        select
            templateid,
            weighted_views_total,
            weighted_usage_total,
            usage_mean,
            usage_stddev,
            capped_usage_total,
            conversion_rate,
            smoothed_conversion_rate,
            normalized_usage,
            normalized_conversion_rate,
            (total_usage_weight * normalized_usage + conversion_rate_weight * normalized_conversion_rate) as combined_score,
            row_number() over (order by combined_score desc nulls last) as rank
        from final_ranking f
        )
        
        select
            templateid
        from combine_scores
        order by rank;
        
        """;
}
