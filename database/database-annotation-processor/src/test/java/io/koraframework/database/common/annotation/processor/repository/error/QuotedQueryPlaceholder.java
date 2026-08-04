package io.koraframework.database.common.annotation.processor.repository.error;

import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface QuotedQueryPlaceholder extends JdbcRepository {

    @Query("""
        SELECT
          ':missing',
          'it''s :escaped',
          ":alsoMissing",
          $$:dollarMissing$$,
          $tag$:tagMissing$tag$,
          `:backtickMissing`,
          [:bracketMissing],
          *
        FROM table
        -- :lineCommentMissing
        WHERE id = :id
        /* :blockCommentMissing */
        """)
    String quotedPlaceholderIsIgnored(long id);
}
