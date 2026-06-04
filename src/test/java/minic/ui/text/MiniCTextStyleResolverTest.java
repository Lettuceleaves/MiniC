package minic.ui.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCTextStyleResolverTest {
    @Test
    void resolvesRoleAndStateToReusableStyleClassesWithLegacyAliases() {
        MiniCTextStyleResolver resolver = MiniCTextStyles.defaultResolver();

        assertThat(resolver.styleClasses(
                MiniCTextStyleRole.CODE_KEYWORD,
                Set.of(MiniCTextStyleState.DIAGNOSTIC, MiniCTextStyleState.DEBUG_EXECUTION)
        )).contains(
                "mc-text-code-keyword",
                "mc-text-state-diagnostic",
                "mc-text-state-debug-execution",
                "token-keyword",
                "diagnostic",
                "debug-execution-range"
        );
    }
}
