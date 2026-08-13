package dev.dynamiq.talli.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTest {

    @Test
    void usesEmailMediaOwnerType() {
        HasMedia email = new Email();

        assertThat(email.mediaOwnerType()).isEqualTo("email");
    }
}
