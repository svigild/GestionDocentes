package com.sergiovd.gestiondocentes.repository;
import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepository extends JpaRepository<PasswordResetToken, Long> {
    PasswordResetToken findByToken(String token);

    // Elimina cualquier token previo asociado a un docente (la columna docente_id es UNIQUE).
    void deleteByDocente(Docente docente);
}