package com.ultikits.plugins.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the UltiChat plugin main class.
 * <p>
 * Tests the lifecycle template-method contract.
 */
@DisplayName("UltiChat main class")
class UltiChatTest {

    @Nested
    @DisplayName("Lifecycle template methods (UltiKits/UltiChat#23)")
    class LifecycleTemplateMethodTests {

        /**
         * UltiTools 6.3.0 makes {@code unregisterSelf()} and {@code reloadSelf()} final
         * template methods that always run the framework's own steps (on unload: the module's
         * {@code onUnregister()} hook, then command and listener unregistration). This module's
         * former {@code unregisterSelf()} override was empty and never called {@code super}, so
         * its commands were never unregistered (UltiKits/UltiChat#23). It is deleted outright;
         * this test pins that neither template method is declared again.
         */
        @Test
        @DisplayName("UltiChat declares neither framework template method")
        void declaresNeitherTemplateMethod() {
            List<String> declared = new ArrayList<>();
            for (Method method : UltiChat.class.getDeclaredMethods()) {
                declared.add(method.getName());
            }

            assertThat(declared).doesNotContain("unregisterSelf", "reloadSelf");
        }
    }
}
