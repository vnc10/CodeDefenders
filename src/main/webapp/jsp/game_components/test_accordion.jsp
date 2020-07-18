<%--

    Copyright (C) 2016-2019 Code Defenders contributors

    This file is part of Code Defenders.

    Code Defenders is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or (at
    your option) any later version.

    Code Defenders is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%--@elvariable id="testAccordion" type="org.codedefenders.beans.game.TestAccordionBean"--%>

<%--
    Displays an accordion of tables of tests, grouped by which of the CUT's methods they cover.

    The accordion is generated by the JSP, the tables in the accordion sections as well as popovers and models are
    generated through JavaScript.
--%>

<%--
<jsp:useBean id="testAccordion" class="org.codedefenders.beans.game.TestAccordionBean" scope="request"/>
--%>

<style type="text/css">
    <%-- Prefix all classes with "ta-" to avoid conflicts.
    We probably want to extract some common CSS when we finally tackle the CSS issue. --%>

    #tests-accordion {
        margin-bottom: 0;
    }

    #tests-accordion .panel-body {
        padding: 0;
    }

    #tests-accordion thead {
        display: none;
    }

    #tests-accordion .dataTables_scrollHead {
        display: none;
    }

    #tests-accordion .panel-heading {
        padding-top: .375em;
        padding-bottom: .375em;
    }

    #tests-accordion td {
        vertical-align: middle;
    }

    #tests-accordion .panel-title.ta-covered {
        color: black;
    }

    #tests-accordion .panel-title:not(.ta-covered) {
        color: #B0B0B0;
    }

    #tests-accordion .ta-column-name {
        color: #B0B0B0;
    }

    #tests-accordion .ta-count {
        margin-right: .5em;
        padding-bottom: .2em;
    }

    #tests-accordion .ta-covered-link,
    #tests-accordion .ta-killed-link {
        color: inherit;
    }
</style>

<div class="panel panel-default">
    <div class="panel-body" id="tests">
        <div class="panel-group" id="tests-accordion">
            <c:forEach var="category" items="${testAccordion.categories}">
                <div class="panel panel-default">
                    <div class="panel-heading" id="ta-heading-${category.id}">
                        <a role="button" data-toggle="collapse" aria-expanded="false"
                           href="#ta-collapse-${category.id}"
                           aria-controls="ta-collapse-${category.id}"
                           class="panel-title ${category.testIds.size() == 0 ? "" : "ta-covered"}"
                           style="text-decoration: none;">
                            <c:if test="${!(category.testIds.size() == 0)}">
                                <span class="label bg-defender ta-count">${category.testIds.size()}</span>
                            </c:if>
                                ${category.description}
                        </a>
                    </div>
                    <div class="panel-collapse collapse" data-parent="#tests-accordion"
                         id="ta-collapse-${category.id}"
                         aria-labelledby="ta-heading-${category.id}">
                        <div class="panel-body">
                            <table id="ta-table-${category.id}" class="table table-sm"></table>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
    </div>
</div>

<script>
    /* Wrap in a function so it has it's own scope. */
    (function () {

        /** Test accordion data. */
        const ta_data = JSON.parse('${testAccordion.JSON}');

        /** A description and list of test ids for each category (method). */
        const categories = ta_data.categories;

        /** Maps test ids to their DTO representation. */
        const tests = new Map(ta_data.tests);

        /** Maps test ids to modals that show the tests' code. */
        const testModals = new Map();

        /* Functions to generate table columns. */
        const genId             = row => 'Test ' + row.id;
        const genCreator        = row => <%-- '<span class="ta-column-name">Creator:</span> ' + --%> row.creatorName;
        const genPoints         = row => '<span class="ta-column-name">Points:</span> '  + row.points;
        const genCoveredMutants = row => '<a class="ta-covered-link"><span class="ta-column-name">Covered:</span> ' + row.coveredMutantIds.length + '</a>';;
        const genKilledMutants  = row => '<a class="ta-killed-link"><span class="ta-column-name">Killed:</span> ' + row.killedMutantIds.length + '</a>';
        const genViewButton     = row => '<button class="ta-view-button btn btn-ssm btn-primary">View</button>';
        const genSmells         = row => {
            const numSmells = row.smells.length;
            let smellLevel;
            let smellColor;
            if (numSmells >= 3) {
                smellLevel = 'Bad';
                smellColor = 'btn-danger';
            } else if (numSmells >= 1) {
                smellLevel = 'Fishy';
                smellColor = 'btn-warning';
            } else {
                smellLevel = 'Good';
                smellColor = 'btn-success';
            }
            return <%-- '<span class="ta-column-name">Smells:</span> '
                + --%> '<a class="ta-smells-link btn btn-ssm ' + smellColor + '">' + smellLevel + '</a>';
        };

        /**
         * Returns the test DTO that describes the row of an element in a DataTables row.
         * @param {HTMLElement} element An HTML element contained in a table row.
         * @param {object} dataTable The DataTable the row belongs to.
         * @return {object} The test DTO the row describes.
         */
        const rowData = function (element, dataTable) {
            const row = $(element).closest('tr');
            return dataTable.row(row).data();
        };

        /**
         * Sets up popovers.
         * @param {object} jqElements A collection of jQuery elements, as returned by $(selector).
         * @param {function} getData Gets called with the HTML element the popover is for,
         *                   returns data to call the other functions with.
         * @param {function} genHeading Generates the heading of the popover.
         * @param {function} genBody Generates the body of the popover.
         */
        const setupPopovers = function (jqElements, getData, genHeading, genBody) {
            jqElements.popover({
                container: document.body,
                template:
                    `<div class="popover" role="tooltip">
                        <div class="arrow"></div>
                        <h3 class="popover-title"></h3>
                        <div class="popover-content" style="max-width: 250px;"></div>
                    </div>`,
                placement: 'top',
                trigger: 'hover',
                html: true,
                title: function () {
                    const data = getData(this);
                    return genHeading(data);
                },
                content: function () {
                    const data = getData(this);
                    return genBody(data);
                }
            });
        };

        /**
         * Creates a modal to display the given test and shows it.
         * References to created models are cached in a map so they don't need to be generated again.
         * @param {object} test The test DTO to display.
         */
        const viewTestModal = function (test) {
            let modal = testModals.get(test.id);
            if (modal !== undefined) {
                modal.modal('show');
                return;
            }

            modal = $(
                `<div class="modal mutant-modal fade" role="dialog">
                    <div class="modal-dialog" style="width: max-content; max-width: 90%; min-width: 500px;">
                        <div class="modal-content">
                            <div class="modal-header">
                                <button type="button" class="close" data-dismiss="modal">&times;</button>
                                <h4 class="modal-title">Test ` + test.id + ` (by ` + test.creatorName + `)</h4>
                            </div>
                            <div class="modal-body">
                                <pre class="readonly-pre"><textarea name="test-` + test.id + `"></textarea></pre>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                            </div>
                        </div>
                    </div>
                </div>`);
            modal.appendTo(document.body);
            testModals.set(test.id, modal);

            const textarea = modal.find('textarea').get(0);
            const editor = CodeMirror.fromTextArea(textarea, {
                lineNumbers: true,
                matchBrackets: true,
                mode: "text/x-java",
                readOnly: true,

            });
            editor.setSize('max-content', 'max-content');

            <%-- TODO: Is there a better solution for this? --%>
            /* Refresh the CodeMirror instance once the modal is displayed.
             * If this is not done, it will display an empty textarea until it is clicked. */
            new MutationObserver((mutations, observer) => {
                for (const mutation of mutations) {
                    if (mutation.type === 'attributes' && mutation.attributeName === 'style') {
                        editor.refresh();
                        observer.disconnect();
                    }
                }
            }).observe(modal.get(0), {attributes: true});

            TestAPI.getAndSetEditorValue(textarea, editor);
            modal.modal('show');
        };

        /* Loop through the categories and create a test table for each one. */
        for (const category of categories) {
            const rows = category.testIds
                .sort()
                .map(tests.get, tests);

            /* Create the DataTable. */
            const tableElement = $('#ta-table-' + category.id);
            const dataTable = tableElement.DataTable({
                data: rows,
                columns: [
                    { data: null, title: '', defaultContent: '' },
                    { data: genId, title: '' },
                    { data: genCreator, title: '' },
                    { data: genCoveredMutants, title: '' },
                    { data: genKilledMutants, title: '' },
                    { data: genPoints, title: '' },
                    { data: genSmells, title: '' },
                    { data: genViewButton, title: '' }
                ],
                scrollY: '400px',
                scrollCollapse: true,
                paging: false,
                dom: 't',
                language: {
                    emptyTable: category.id === 'all'
                        ? 'No tests.'
                        : 'No tests cover this method.'
                }
            });

            /* Assign function to the "View" buttons. */
            tableElement.on('click', '.ta-view-button', function () {
                const test = rowData(this, dataTable);
                viewTestModal(test);
            });

            setupPopovers(
                tableElement.find('.ta-covered-link'),
                that => rowData(that, dataTable).coveredMutantIds,
                coveredIds => coveredIds.length > 0
                    ? 'Covered Mutants'
                    : null,
                coveredIds => coveredIds.length > 0
                    ? coveredIds.join(', ')
                    : null,
            );

            setupPopovers(
                tableElement.find('.ta-killed-link'),
                that => rowData(that, dataTable).killedMutantIds,
                killedIds => killedIds.length > 0
                    ? 'Killed Mutants'
                    : null,
                killedIds => killedIds.length > 0
                    ? killedIds.join(', ')
                    : null,
            );

            setupPopovers(
                tableElement.find('.ta-smells-link'),
                that => rowData(that, dataTable).smells,
                smells => smells.length > 0
                    ? 'This test smells of'
                    : null,
                smells => smells.length > 0
                    ? smells.join('<br>')
                    : 'This test does not have any smells.'
            );
        }
    })();
</script>
