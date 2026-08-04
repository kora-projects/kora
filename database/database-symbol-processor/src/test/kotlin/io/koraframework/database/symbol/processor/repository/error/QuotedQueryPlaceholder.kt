package io.koraframework.database.symbol.processor.repository.error

import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface QuotedQueryPlaceholder : JdbcRepository {

    @Query("SELECT ':missing', 'it''s :escaped', \":alsoMissing\", \$\$:dollarMissing\$\$, \$tag\$:tagMissing\$tag\$, `:backtickMissing`, [:bracketMissing], * FROM table -- :lineCommentMissing\n WHERE id = :id /* :blockCommentMissing */")
    fun quotedPlaceholderIsIgnored(id: Long): String
}
