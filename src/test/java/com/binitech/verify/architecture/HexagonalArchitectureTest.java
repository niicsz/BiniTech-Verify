package com.binitech.verify.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HexagonalArchitectureTest {
  @TempDir Path compiled;

  @Test
  @DisplayName("Domínio e aplicação compilam sozinhos, sem Spring, Mongo nem adaptadores")
  void coreCompilesWithoutFrameworksAndDependenciesPointInward() throws Exception {
    compile("domain");
    compile("application");
  }

  private void compile(String layer) throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Use um JDK para checar a arquitetura");
    var args =
        new ArrayList<>(
            List.of(
                "--release",
                "25",
                "-encoding",
                "UTF-8",
                "-classpath",
                compiled.toString(),
                "-sourcepath",
                compiled.toString(),
                "-d",
                compiled.toString()));
    try (var sources = Files.walk(Path.of("src/main/java/com/binitech/verify", layer))) {
      args.addAll(sources.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList());
    }
    assertEquals(
        0,
        compiler.run(null, null, null, args.toArray(String[]::new)),
        layer + " não pode depender de adaptadores, Spring ou Mongo");
  }
}
