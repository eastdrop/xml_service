package com.xmlservice.xmlservice.controller;

import com.xmlservice.xmlservice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Scanner;

@Component
@RequiredArgsConstructor
public class ConsoleController implements CommandLineRunner {

    private final XmlParserService xmlParserService;
    private final DatabaseService databaseService;
    private final SchemaAnalyzerService schemaAnalyzerService;
    private final JaxbXmlParserService jaxbXmlParserService;
    private final RepositoryService repositoryService;

    @Override
    public void run(String... args) {
        if (args.length > 0) {
            processCommandLineArgs(args);
        } else {
            startInteractiveMode();
        }
    }

    private void processCommandLineArgs(String[] args) {
        switch (args[0].toLowerCase()) {
            case "gettables":
                System.out.println("Available tables: " + xmlParserService.getTableNames());
                break;

            case "getddl":
                if (args.length > 1) {
                    String ddl = xmlParserService.getTableDDL(args[1]);
                    System.out.println("DDL for " + args[1] + ":\n" + ddl);
                } else {
                    System.out.println("Usage: getddl <tableName>");
                }
                break;

            case "update":
                if (args.length > 1) {
                    databaseService.update(args[1]);
                    System.out.println("Table " + args[1] + " updated successfully");
                } else {
                    databaseService.update();
                    System.out.println("All tables updated successfully");
                }
                break;

            case "getcolumns":
                if (args.length > 1) {
                    System.out.println("Columns for " + args[1] + ": " +
                            xmlParserService.getColumnNames(args[1]));
                }
                break;

            case "checkid":
                if (args.length > 2) {
                    boolean isId = xmlParserService.isColumnId(args[1], args[2]);
                    System.out.println("Column " + args[2] + " is ID: " + isId);
                }
                break;
            case "analyze":
                if (args.length > 1) {
                    var result = schemaAnalyzerService.compareSchemas(args[1]);
                    printSchemaComparison(result);
                } else {
                    System.out.println("Usage: analyze <tableName>");
                }
                break;

            case "migration":
                if (args.length > 1) {
                    String sql = schemaAnalyzerService.generateMigrationSql(args[1]);
                    if (sql != null) {
                        System.out.println("Migration SQL for " + args[1] + ":\n" + sql);
                    } else {
                        System.out.println("No migration required for " + args[1]);
                    }
                } else {
                    System.out.println("Usage: migration <tableName>");
                }
                break;

            case "stats":
                if (args.length > 1) {
                    Map<String, Object> stats = schemaAnalyzerService.getTableStatistics(args[1]);
                    System.out.println("Statistics for " + args[1] + ":");
                    stats.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                } else {
                    System.out.println("Usage: stats <tableName>");
                }
                break;

            case "jaxb-parse":
                try {
                    var catalog = jaxbXmlParserService.fetchAndParseWithJaxb();
                    System.out.println("Parsed XML catalog via JAXB:");
                    System.out.println("  Date: " + catalog.getDate());
                    if (catalog.getShop() != null) {
                        System.out.println("  Shop: " + catalog.getShop().getName());
                        System.out.println("  Company: " + catalog.getShop().getCompany());
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;

            case "jaxb-info":
                try {
                    var info = jaxbXmlParserService.getCatalogInfo();
                    System.out.println("XML Catalog Info:");
                    info.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;

            case "sync":
                System.out.println("Starting synchronization from XML...");
                try {
                    repositoryService.syncFromXmlCatalog();
                    System.out.println("Synchronization completed successfully");
                } catch (Exception e) {
                    System.out.println("Synchronization failed: " + e.getMessage());
                }
                break;

            default:
                System.out.println("Unknown command. Available commands:");
                System.out.println("  gettables - List all tables");
                System.out.println("  getddl <table> - Get DDL for table");
                System.out.println("  update [table] - Update all or specific table");
                System.out.println("  getcolumns <table> - Get column names");
                System.out.println("  checkid <table> <column> - Check if column is ID");
        }
    }

    private void startInteractiveMode() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== XML to Database Service ===");
            System.out.println("1. List tables");
            System.out.println("2. Get table DDL");
            System.out.println("3. Update database");
            System.out.println("4. Get column names");
            System.out.println("5. Check ID column");
            System.out.println("6. Analyze table schema");
            System.out.println("7. Generate migration SQL");
            System.out.println("8. Get table statistics");
            System.out.println("9. Parse XML with JAXB");
            System.out.println("10. Get XML catalog info");
            System.out.println("11. Synchronize from XML");
            System.out.println("0. Exit");
            System.out.print("Select option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("Tables: " + xmlParserService.getTableNames());
                    break;

                case "2":
                    System.out.print("Enter table name: ");
                    String tableName = scanner.nextLine();
                    System.out.println(xmlParserService.getTableDDL(tableName));
                    break;

                case "3":
                    System.out.print("Update specific table? (y/N): ");
                    String specific = scanner.nextLine();
                    if (specific.equalsIgnoreCase("y")) {
                        System.out.print("Enter table name: ");
                        String table = scanner.nextLine();
                        databaseService.update(table);
                        System.out.println("Table " + table + " updated");
                    } else {
                        databaseService.update();
                        System.out.println("All tables updated");
                    }
                    break;

                case "4":
                    System.out.print("Enter table name: ");
                    String tableForColumns = scanner.nextLine();
                    System.out.println("Columns: " + xmlParserService.getColumnNames(tableForColumns));
                    break;

                case "5":
                    System.out.print("Enter table name: ");
                    String tableForId = scanner.nextLine();
                    System.out.print("Enter column name: ");
                    String column = scanner.nextLine();
                    System.out.println("Is ID: " + xmlParserService.isColumnId(tableForId, column));
                    break;

                case "6":
                    System.out.print("Enter table name: ");
                    String tableToAnalyze = scanner.nextLine();
                    var analysisResult = schemaAnalyzerService.compareSchemas(tableToAnalyze);
                    printSchemaComparison(analysisResult);
                    break;

                case "7":
                    System.out.print("Enter table name: ");
                    String tableForMigration = scanner.nextLine();
                    String migrationSql = schemaAnalyzerService.generateMigrationSql(tableForMigration);
                    if (migrationSql != null) {
                        System.out.println("Migration SQL:\n" + migrationSql);
                    } else {
                        System.out.println("No migration required");
                    }
                    break;

                case "8":
                    System.out.print("Enter table name: ");
                    String tableForStats = scanner.nextLine();
                    Map<String, Object> statistics = schemaAnalyzerService.getTableStatistics(tableForStats);
                    System.out.println("Table Statistics:");
                    statistics.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                    break;

                case "9":
                    try {
                        var catalog = jaxbXmlParserService.fetchAndParseWithJaxb();
                        System.out.println("Parsed XML catalog via JAXB:");
                        System.out.println("  Date: " + catalog.getDate());
                        if (catalog.getShop() != null) {
                            System.out.println("  Shop: " + catalog.getShop().getName());
                            System.out.println("  Currencies: " +
                                    (catalog.getShop().getCurrencies() != null &&
                                            catalog.getShop().getCurrencies().getCurrencyList() != null ?
                                            catalog.getShop().getCurrencies().getCurrencyList().size() : 0));
                            System.out.println("  Categories: " +
                                    (catalog.getShop().getCategories() != null &&
                                            catalog.getShop().getCategories().getCategoryList() != null ?
                                            catalog.getShop().getCategories().getCategoryList().size() : 0));
                            System.out.println("  Offers: " +
                                    (catalog.getShop().getOffers() != null &&
                                            catalog.getShop().getOffers().getOfferList() != null ?
                                            catalog.getShop().getOffers().getOfferList().size() : 0));
                        }
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                    break;

                case "10":
                    try {
                        var info = jaxbXmlParserService.getCatalogInfo();
                        System.out.println("XML Catalog Info:");
                        info.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                    break;

                case "11":
                    System.out.print("Are you sure you want to synchronize from XML? (y/N): ");
                    String confirm = scanner.nextLine();
                    if (confirm.equalsIgnoreCase("y")) {
                        System.out.println("Starting synchronization...");
                        try {
                            repositoryService.syncFromXmlCatalog();
                            System.out.println("Synchronization completed successfully");
                        } catch (Exception e) {
                            System.out.println("Synchronization failed: " + e.getMessage());
                        }
                    }
                    break;

                case "0":
                    System.out.println("Goodbye!");
                    scanner.close();
                    return;

                default:
                    System.out.println("Invalid option");
            }
        }
    }
    private void printSchemaComparison(SchemaAnalyzerService.SchemaComparisonResult result) {
        System.out.println("=== Schema Analysis for '" + result.getTableName() + "' ===");
        System.out.println("Table exists in DB: " + result.isTableExists());
        System.out.println("Schema changed: " + result.isSchemaChanged());
        System.out.println("Change type: " + result.getChangeType());
        System.out.println("Changes found: " + result.getChanges().size());

        if (!result.getChanges().isEmpty()) {
            System.out.println("\nDetailed changes:");
            for (var change : result.getChanges()) {
                System.out.println("  - " + change.getDescription() + " [" + change.getChangeType() + "]");
            }
        }

        if (result.getXmlSchema() != null) {
            System.out.println("\nXML Schema columns: " + result.getXmlSchema().getColumns().size());
        }

        if (result.getDbSchema() != null) {
            System.out.println("DB Schema columns: " + result.getDbSchema().getColumns().size());
        }
    }
}