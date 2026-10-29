package com.sparkz.sparkz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Pre-existing: without this, the default profile tries to resolve SUPABASE_DB_URL,
// which isn't set in CI/local test runs. Activating "test" points at the isolated H2
// datasource added alongside the profile/skills work.
@SpringBootTest
@ActiveProfiles("test")
class SparkzApplicationTests {

	@Test
	void contextLoads() {
	}

}
