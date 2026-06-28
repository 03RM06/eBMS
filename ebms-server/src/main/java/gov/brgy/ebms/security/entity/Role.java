package gov.brgy.ebms.security.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name_en", nullable = false, length = 64)
    private String nameEn;

    @Column(name = "name_fil", nullable = false, length = 64)
    private String nameFil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Role() {}

    public Role(String code, String nameEn, String nameFil) {
        this.code = code;
        this.nameEn = nameEn;
        this.nameFil = nameFil;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameFil() { return nameFil; }
    public void setNameFil(String nameFil) { this.nameFil = nameFil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
