/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.centraldogma.server.internal.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.linecorp.armeria.server.HttpResponseException;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.centraldogma.common.Author;
import com.linecorp.centraldogma.server.internal.admin.auth.User;
import com.linecorp.centraldogma.server.internal.metadata.MetadataService;
import com.linecorp.centraldogma.server.internal.metadata.MigrationUtil;
import com.linecorp.centraldogma.server.internal.metadata.Token;
import com.linecorp.centraldogma.testing.internal.ProjectManagerRule;

import io.netty.util.internal.StringUtil;

public class TokenServiceTest {

    @ClassRule
    public static final ProjectManagerRule rule = new ProjectManagerRule() {
        @Override
        protected void afterExecutorStarted() {
            MigrationUtil.migrate(projectManager(), executor());
        }
    };

    private static final Author adminAuthor = new Author("admin@localhost.com");
    private static final Author guestAuthor = new Author("guest@localhost.com");
    private static final User admin = new User("admin@localhost.com", User.LEVEL_ADMIN);
    private static final User guest = new User("guest@localhost.com");

    private static TokenService tokenService;

    private final ServiceRequestContext ctx = mock(ServiceRequestContext.class);

    @BeforeClass
    public static void beforeClass() {
        tokenService = new TokenService(rule.projectManager(), rule.executor(),
                                        new MetadataService(rule.projectManager(), rule.executor()));
    }

    @Test
    public void adminToken() {
        final Token token = tokenService.createToken("forAdmin1", true, adminAuthor, admin).join().object();
        assertThat(token.isActive()).isTrue();
        assertThatThrownBy(() -> tokenService.createToken("forAdmin2", true, guestAuthor, guest)
                                             .join())
                .isInstanceOf(IllegalArgumentException.class);

        final Collection<Token> tokens = tokenService.listTokens(admin).join();
        assertThat(tokens.stream().filter(t -> !StringUtil.isNullOrEmpty(t.secret()))).hasSize(1);

        assertThatThrownBy(() -> tokenService.deleteToken(ctx, "forAdmin1", guestAuthor, guest)
                                             .join())
                .hasCauseInstanceOf(HttpResponseException.class);

        assertThat(tokenService.deleteToken(ctx, "forAdmin1", adminAuthor, admin).join()).satisfies(t -> {
            assertThat(t.appId()).isEqualTo(token.appId());
            assertThat(t.isAdmin()).isEqualTo(token.isAdmin());
            assertThat(t.creation()).isEqualTo(token.creation());
            assertThat(t.deactivation()).isEqualTo(token.deactivation());
        });
    }

    @Test
    public void userToken() {
        final Token userToken1 = tokenService.createToken("forUser1", false, adminAuthor, admin)
                                             .join().object();
        final Token userToken2 = tokenService.createToken("forUser2", false, guestAuthor, guest)
                                             .join().object();
        assertThat(userToken1.isActive()).isTrue();
        assertThat(userToken2.isActive()).isTrue();

        final Collection<Token> tokens = tokenService.listTokens(guest).join();
        assertThat(tokens.stream().filter(token -> !StringUtil.isNullOrEmpty(token.secret())).count())
                .isEqualTo(0);

        assertThat(tokenService.deleteToken(ctx, "forUser1", adminAuthor, admin).join()).satisfies(t -> {
            assertThat(t.appId()).isEqualTo(userToken1.appId());
            assertThat(t.isAdmin()).isEqualTo(userToken1.isAdmin());
            assertThat(t.creation()).isEqualTo(userToken1.creation());
            assertThat(t.deactivation()).isEqualTo(userToken1.deactivation());
        });
        assertThat(tokenService.deleteToken(ctx, "forUser2", guestAuthor, guest).join()).satisfies(t -> {
            assertThat(t.appId()).isEqualTo(userToken2.appId());
            assertThat(t.isAdmin()).isEqualTo(userToken2.isAdmin());
            assertThat(t.creation()).isEqualTo(userToken2.creation());
            assertThat(t.deactivation()).isEqualTo(userToken2.deactivation());
        });
    }
}
