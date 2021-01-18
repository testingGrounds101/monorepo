/**********************************************************************************
 *
 * Copyright (c) 2015 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.pages.tool.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.sakaiproject.pages.tool.model.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

public class PagesStorage {
    private static final Logger LOG = LoggerFactory.getLogger(PagesStorage.class);

    public Optional<Page> getForContext(final String context) {
        return DB.transaction
            ("Find a page by context",
                new DBAction<Optional<Page>>() {
                    @Override
                    public Optional<Page> call(DBConnection db) throws SQLException {
                        try (DBResults results = db.run("SELECT * from pages_page WHERE context = ?")
                            .param(context)
                            .executeQuery()) {
                            for (ResultSet result : results) {
                                return Optional.of(new Page(result.getString("context"),
                                    result.getString("title"),
                                    result.getString("content")));
                            }

                            return Optional.empty();
                        }
                    }
                }
            );
    }


    public String createPage(Page page) {
        return DB.transaction("Create a page",
            new DBAction<String>() {
                @Override
                public String call(DBConnection db) throws SQLException {
                    db.run("INSERT INTO pages_page (context, title, content) VALUES (?, ?, ?)")
                        .param(page.getContext())
                        .param(page.getTitle())
                        .param(page.getContent())
                        .executeUpdate();

                    db.commit();

                    return page.getContext();
                }
            }
        );
    }


    public void updatePage(Page page) {
        try {
            final String context = page.getContext();

            DB.transaction("Update page with context " + context,
                new DBAction<Void>() {
                    @Override
                    public Void call(DBConnection db) throws SQLException {
                        db.run("UPDATE pages_page SET title = ?, content = ? WHERE context = ?")
                            .param(page.getTitle())
                            .param(page.getContent())
                            .param(context)
                            .executeUpdate();

                        db.commit();

                        return null;
                    }
                }
            );
        } catch (Exception e) {
            throw new RuntimeException("Wha?", e);
        }
    }
}