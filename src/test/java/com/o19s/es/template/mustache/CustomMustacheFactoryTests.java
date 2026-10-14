/*
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

package com.o19s.es.template.mustache;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.lucene.tests.util.LuceneTestCase;

import com.github.mustachejava.Mustache;

public class CustomMustacheFactoryTests extends LuceneTestCase {

    public void testPartialTemplatesAreBlocked() throws Exception {
        Path secret = createTempFile("ltr-secret", ".txt");
        Files.writeString(secret, "LEAKED_CONTENT");

        String template = "{{>file://" + secret.toAbsolutePath() + "}}";

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> MustacheUtils.compile("t", template));
        assertThat(e.getMessage(), containsString("Partial templates are not supported"));
    }

    public void testRelativePartialTemplatesAreBlocked() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> MustacheUtils.compile("t", "{{>nonexistent-partial}}")
        );
        assertThat(e.getMessage(), containsString("Partial templates are not supported"));
    }

    public void testInlineTemplateStillRenders() {
        Mustache m = MustacheUtils.compile("t", "hello {{name}}");
        String out = MustacheUtils.execute(m, Collections.singletonMap("name", "world"));
        assertThat(out, containsString("hello world"));
        assertThat(out, not(containsString("{{")));
    }
}
