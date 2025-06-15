package com.frever.cmsAuthorization.entities;

import java.util.List;
import java.util.Objects;
import jakarta.persistence.*;

@Entity
@Table(name = "role", schema = "cms")
public class Role {
    @Id
    @SequenceGenerator(name = "roleSeq", sequenceName = "role_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roleSeq")
    private Integer id;

    private String name;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "role_access_scope", schema = "cms", joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "access_scope_id"))
    private List<AccessScope> accessScopes;

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<AccessScope> getAccessScopes() {
        return accessScopes;
    }

    public void setAccessScopes(List<AccessScope> accessScopes) {
        this.accessScopes = accessScopes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id) && Objects.equals(name, role.name)
            && Objects.equals(accessScopes, role.accessScopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, accessScopes);
    }

    @Override
    public String toString() {
        return "Role{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", accessScopes=" + accessScopes +
            '}';
    }
}
