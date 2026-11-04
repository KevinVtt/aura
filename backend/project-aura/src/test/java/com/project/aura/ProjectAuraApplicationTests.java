package com.project.aura;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Levanta la aplicacion real contra PostgreSQL: sin DB_PASS en el entorno falla
// y no dice nada util sobre el codigo. La suite de unitarios ya cubre la logica.
@Disabled("requiere PostgreSQL y las variables del .env")
@SpringBootTest
class ProjectAuraApplicationTests {

	@Test
	void contextLoads() {
	}

}
