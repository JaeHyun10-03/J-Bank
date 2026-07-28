package com.jbank.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** 폴더구조 문서 4절의 의존 방향 규칙을 강제한다. 여기서 막힌 조합은 전부 금지 대상이다. */
class DependencyDirectionTest {

  private static final String BASE = "com.jbank";

  private final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(BASE);

  @Test
  void globalDoesNotDependOnDomainPackages() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(BASE + ".global..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                BASE + ".customer..",
                BASE + ".account..",
                BASE + ".ledger..",
                BASE + ".transfer..",
                BASE + ".product..",
                BASE + ".auth..",
                BASE + ".support..",
                BASE + ".batch..");
    rule.check(classes);
  }

  @Test
  void accountAndLedgerDoNotDependOnEachOther() {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".account..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(BASE + ".ledger..")
        .check(classes);

    noClasses()
        .that()
        .resideInAPackage(BASE + ".ledger..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(BASE + ".account..")
        .check(classes);
  }

  @Test
  void supportOnlyDependsOnCommon() {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".support..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            BASE + ".customer..",
            BASE + ".account..",
            BASE + ".ledger..",
            BASE + ".transfer..",
            BASE + ".product..",
            BASE + ".auth..",
            BASE + ".batch..")
        .check(classes);
  }

  @Test
  void customerDoesNotDependOnAccount() {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".customer..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(BASE + ".account..")
        .check(classes);
  }
}
