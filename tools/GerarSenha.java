import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Execute em terminal interativo; nunca recebe a senha em argumento ou variável de ambiente. */
public class GerarSenha {
    public static void main(String[] args) {
        Console terminal = System.console();
        if (terminal == null || args.length != 0) {
            System.err.println("Execute sem argumentos em um terminal interativo (PowerShell, Terminal ou cmd).");
            System.exit(1);
        }
        String hash = gerarHash(terminal);
        if (hash == null) System.exit(1);
        System.out.println("Hash BCrypt para ADMIN_PASSWORD_HASH (copie o valor completo):");
        System.out.println(hash);
    }

    private static String gerarHash(Console terminal) {
        char[] senha = terminal.readPassword("Nova senha administrativa (12+ caracteres): ");
        char[] confirmacao = terminal.readPassword("Confirme a senha: ");
        try {
            if (senha == null || confirmacao == null || !Arrays.equals(senha, confirmacao)) {
                System.err.println("As senhas não conferem.");
                return null;
            }
            String valor = new String(senha);
            if (senha.length < 12 || valor.getBytes(StandardCharsets.UTF_8).length > 72) {
                System.err.println("Use pelo menos 12 caracteres e no máximo 72 bytes UTF-8 (limite BCrypt).");
                return null;
            }
            return new BCryptPasswordEncoder(12).encode(valor);
        } finally {
            if (senha != null) Arrays.fill(senha, '\0');
            if (confirmacao != null) Arrays.fill(confirmacao, '\0');
        }
    }
}
