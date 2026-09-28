# Demo — Build multi-ambiente de WAR

Proyecto Spring Boot 4.1.1 / Java 21 configurado para generar un WAR por ambiente
(`develop`, `test`, `prod`) y depositarlo automáticamente en carpetas separadas
dentro de `dist/`.

---

## 1. Qué hace

- Genera un WAR desplegable en Tomcat externo **y** ejecutable con `java -jar`.
- Separa la salida por ambiente:
  - `dist/WAR-DEV/`
  - `dist/WAR-TEST/`
  - `dist/WAR-PROD/<fecha-hora>_prod/`
- Las carpetas se crean solas en cada build; no hace falta crearlas a mano.
- El WAR de `prod` va dentro de una subcarpeta con timestamp, para no pisar builds anteriores.

---

## 2. Requisitos

| Herramienta | Versión | Nota |
| --- | --- | --- |
| JDK | 21 | Spring Boot 4 lo exige |
| Maven | 3.6.3+ | o usar `mvnw` / `mvnw.cmd` del proyecto |
| Tomcat | 11+ | Spring Boot 4 exige baseline Servlet 6.1 |
| Eclipse | m2e (y opcional Spring Tools) | solo para los botones de launch |

---

## 3. Generar los WAR

```bash
mvn clean package -Pdevelop
mvn clean package -Ptest
mvn clean package -Pprod
```

Resultado:

```text
dist/WAR-DEV/demo-0.0.1-SNAPSHOT.war
dist/WAR-TEST/demo-0.0.1-SNAPSHOT.war
dist/WAR-PROD/20260925-143012_prod/demo-0.0.1-SNAPSHOT.war
```

Notas:

- Las carpetas y subcarpetas se crean automáticamente (el `mkdir` de Ant crea también
  los directorios padre que no existan).
- `dist/` está en `.gitignore`, así que los WAR generados **no** se commitean.
- Para correr el WAR standalone: `java -jar dist/WAR-PROD/.../demo-0.0.1-SNAPSHOT.war`
- Verificación rápida con el endpoint `GET /ping` (devuelve `pong - <ambiente>`).

---

## 4. Profiles de Maven

| Profile | `env` | `war.output.dir` |
| --- | --- | --- |
| `develop` (activo por defecto) | `develop` | `${wars.base.dir}/WAR-DEV` |
| `test` | `test` | `${wars.base.dir}/WAR-TEST` |
| `prod` | `prod` | `${wars.base.dir}/WAR-PROD/${maven.build.timestamp}_prod` |

### Cómo funciona

- `wars.base.dir` = `${project.basedir}/dist` (portable: siempre relativo al proyecto).
- `maven.build.timestamp.format` = `yyyyMMdd-HHmmss` define el formato del timestamp.
- `maven-antrun-plugin` corre en la fase `package` y copia el WAR a `war.output.dir`
  con `<mkdir>` + `<copy>`. Está declarado **después** de `spring-boot-maven-plugin`
  para copiar el WAR ya repackageado.
- El `develop` es `activeByDefault`, así que `mvn package` sin `-P` genera el de dev.

### Config por ambiente

- `src/main/resources/application.properties` contiene:

  ```properties
  spring.profiles.active=@env@
  ```

- El `spring-boot-starter-parent` ya filtra los `application*.properties` usando el
  delimitador `@`, por lo que `@env@` se reemplaza por el valor del profile Maven.
- Los valores de cada ambiente viven en `application-develop.properties`,
  `application-test.properties` y `application-prod.properties`.

---

## 5. Llevarlo a otro proyecto

### Snippets del `pom.xml`

El proyecto destino debe usar `spring-boot-starter-parent` como parent.

**a) Packaging**

```xml
<packaging>war</packaging>
```

**b) Properties**

```xml
<properties>
	<java.version>21</java.version>
	<maven.build.timestamp.format>yyyyMMdd-HHmmss</maven.build.timestamp.format>
	<wars.base.dir>${project.basedir}/dist</wars.base.dir>
</properties>
```

**c) Dependencias**

```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-tomcat</artifactId>
	<scope>provided</scope>
</dependency>
```

**d) Build**

```xml
<build>
	<plugins>
		<plugin>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-maven-plugin</artifactId>
		</plugin>
		<plugin>
			<groupId>org.apache.maven.plugins</groupId>
			<artifactId>maven-antrun-plugin</artifactId>
			<version>3.2.0</version>
			<executions>
				<execution>
					<id>copy-war</id>
					<phase>package</phase>
					<goals>
						<goal>run</goal>
					</goals>
					<configuration>
						<target>
							<mkdir dir="${war.output.dir}"/>
							<copy file="${project.build.directory}/${project.build.finalName}.war" todir="${war.output.dir}"/>
						</target>
					</configuration>
				</execution>
			</executions>
		</plugin>
	</plugins>
</build>
```

**e) Profiles**

```xml
<profiles>
	<profile>
		<id>develop</id>
		<activation>
			<activeByDefault>true</activeByDefault>
		</activation>
		<properties>
			<env>develop</env>
			<war.output.dir>${wars.base.dir}/WAR-DEV</war.output.dir>
		</properties>
	</profile>
	<profile>
		<id>test</id>
		<properties>
			<env>test</env>
			<war.output.dir>${wars.base.dir}/WAR-TEST</war.output.dir>
		</properties>
	</profile>
	<profile>
		<id>prod</id>
		<properties>
			<env>prod</env>
			<war.output.dir>${wars.base.dir}/WAR-PROD/${maven.build.timestamp}_prod</war.output.dir>
		</properties>
	</profile>
</profiles>
```

**f) Main class**

```java
@SpringBootApplication
public class MiAplicacion extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(MiAplicacion.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(MiAplicacion.class, args);
	}
}
```

Package correcto en Spring Boot 4:
`org.springframework.boot.web.servlet.support.SpringBootServletInitializer`.

**g) Config por ambiente**

```properties
# src/main/resources/application.properties
spring.profiles.active=@env@
```

```properties
# src/main/resources/application-develop.properties
app.environment=develop
```

```properties
# src/main/resources/application-test.properties
app.environment=test
```

```properties
# src/main/resources/application-prod.properties
app.environment=prod
```

### Consideraciones de los snippets

- El parent debe ser `spring-boot-starter-parent`: habilita el filtrado `@...@` y
  gestiona las versiones de los plugins.
- El `maven-antrun-plugin` va **después** del `spring-boot-maven-plugin`. Dentro de la
  fase `package` Maven ejecuta en orden de declaración, y hay que copiar el WAR ya
  repackageado.
- `maven.build.timestamp.format` es **global**: afecta a todo uso de
  `${maven.build.timestamp}` en el pom.
- `spring-boot-starter-tomcat` con `<scope>provided</scope>` es obligatorio. Si no, el
  Tomcat embebido viaja dentro del WAR y choca con el contenedor externo.
- **Tomcat 11+**: Spring Boot 4 exige Servlet 6.1.
- Ajustar `<artifactId>` / `finalName` si el proyecto tiene otro nombre; el WAR se llama
  según `${project.build.finalName}`.
- Si el proyecto ya tiene `<build>` o `<profiles>`, **fusionar** los bloques, no
  duplicarlos. No repetir IDs de profiles existentes.
- Agregar `dist/` al `.gitignore` del proyecto destino.
- El `spring-boot-starter` genérico queda redundante si ya usás `spring-boot-starter-web`.
- Guardar los `.properties` en **UTF-8 sin BOM** (el BOM rompe la primera clave).
- El filtrado de `@env@` requiere que `application.properties` esté en
  `src/main/resources`.

---

## 6. Eclipse: crear y compartir los launch

Las Run Configurations son **locales por defecto** (viven en el `.metadata` del
workspace y no se comparten). Para que viajen con el proyecto hay que marcarlas como
*shared*.

### Crear

1. Importar el proyecto como *Existing Maven Project*.
2. *Run → Run Configurations... → Maven Build → New*.
3. Completar:
   - **Name**: `war-prod` (o `war-test`, `war-dev`)
   - **Goals**: `clean package`
   - **Profiles**: `prod` (o `test`, `develop`)
   - **JRE**: Java 21
4. Apply. Repetir para los tres ambientes.

### Compartir

1. En la configuración, pestaña **Common** (la última).
2. Sección **Shared file** → elegir el proyecto.
3. Apply. Eclipse deja el `.launch` en la raíz del proyecto.
4. Commitearlo: `.gitignore` no ignora `*.launch`, así que se trackea solo.

Del otro lado: al importar el proyecto, los `.launch` aparecen en
*Run → Run Configurations → Maven Build* y se ejecutan con doble click. Si no
aparecen al instante, hacer *F5* sobre el proyecto.

---

## 7. Nota sobre `.project`

- El `.gitignore` (sección STS) ignora `.project`, `.classpath` y `.settings`, por lo
  que **no viajan** al repositorio.
- Los `.launch` usan `WORKING_DIRECTORY=${project_loc:demo}`, que resuelve **por nombre
  de proyecto** (`demo`).
- Si el otro usuario importa el proyecto con el nombre `demo`, funciona sin más. Si lo
  renombra al importar, el working directory no resuelve y hay que corregirlo a mano.
- Opciones:
  - **Recomendada**: sacar `.project` del `.gitignore` y commitearlo, fijando el nombre
    `demo` para todos.
  - Alternativa: dejar todo como está e importar siempre con el nombre `demo`.

---

## 8. Troubleshooting

| Síntoma | Causa / solución |
| --- | --- |
| El WAR no levanta en el servidor | Tomcat 9/10 no sirve. Necesitás Tomcat 11+ (Servlet 6.1). |
| `GET /ping` da 404 | El proyecto no tiene endpoints; `PingController` expone `/ping`. |
| No veo la carpeta `dist/` en Eclipse | Refrescar con *F5*. Eclipse no la detecta sola. |
| `${project_loc:demo}` no resuelve | El proyecto en Eclipse no se llama `demo`. |
| Falla el `maven-antrun-plugin` | El Maven embebido de m2e es < 3.6.3. Apuntar a un Maven instalado. |
| La primera clave de un `.properties` se lee mal | El archivo tiene BOM. Guardarlo en UTF-8 sin BOM. |
| El WAR sale con el Tomcat embebido adentro | Falta `<scope>provided</scope>` en `spring-boot-starter-tomcat`. |
