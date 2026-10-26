package com.expensetracker.service;

import com.expensetracker.dto.CategoryRequest;
import com.expensetracker.dto.CategoryResponse;
import com.expensetracker.exception.ForbiddenException;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public CategoryResponse createCategory(CategoryRequest request, User user) {
        Category category = Category.builder()
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .user(user)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    public List<CategoryResponse> getUserCategories(User user) {
        return categoryRepository.findByUserIdOrUserIsNull(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse updateCategory(Long id, CategoryRequest request, User user) {
        Category category = findOwnedCategory(id, user);

        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());

        Category updated = categoryRepository.save(category);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCategory(Long id, User user) {
        Category category = findOwnedCategory(id, user);

        expenseRepository.clearCategory(category.getId());
        categoryRepository.delete(category);
    }

    /** Global categories (user == null) are read-only; other users' categories are reported as missing. */
    private Category findOwnedCategory(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (category.getUser() == null) {
            throw new ForbiddenException("Default categories cannot be modified");
        }
        if (!category.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Category not found");
        }
        return category;
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .global(category.getUser() == null)
                .build();
    }
}
