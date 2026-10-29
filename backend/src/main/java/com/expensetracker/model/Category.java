package com.expensetracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Document("categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category implements IdentifiedDocument {

    @Id
    private Long id;

    @NotBlank
    private String name;

    private String icon;

    private String color;

    @DocumentReference
    private User user;
}
