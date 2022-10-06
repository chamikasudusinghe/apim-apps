/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

describe("Upload api spec from the api definition page", () => {
    const publisher = 'publisher';
    const password = 'test123';
    const carbonUsername = 'admin';
    const carbonPassword = 'admin';
    const apiName = 'newapi' + Math.floor(Date.now() / 1000);
    const apiVersion = '1.0.0';

    before(function () {
        //cy.carbonLogin(carbonUsername, carbonPassword);
        //cy.addNewUser(publisher, ['Internal/publisher', 'Internal/creator', 'Internal/everyone'], password);

    })

    it.only("Upload api spec from the api definition page", () => {
        cy.loginToPublisher(publisher, password);
        cy.createAPIByRestAPIDesign(apiName, apiVersion);
        cy.get('[data-testid="left-menu-itemAPIdefinition"]').click();
        cy.get('[data-testid="import-definition-btn"]').click();
        cy.get('[data-testid="open-api-file-select-radio"]').click();

        // upload the swagger
        cy.get('[data-testid="browse-to-upload-btn"]').then(function () {
            const filepath = 'api_artifacts/swagger_2.0_.json'
            cy.get('input[type="file"]').attachFile(filepath);
            // provide the swagger url
            cy.get('[data-testid="import-open-api-btn"]').click();

            // Wait until the api is saved
            cy.intercept('**/apis/**').as('apiGet');
            cy.wait('@apiGet');

            // Check the resource exists
            cy.get('[data-testid="left-menu-itemresources"]').click();
            cy.get('[data-testid="operation-/toy/{petId}/uploadImage-post"]', { timeout: 30000 });
            cy.get('[data-testid="operation-/toy/{petId}/uploadImage-post"]').should('be.visible');
        });

    });

    after(function () {
        // Test is done. Now delete the api
        cy.deleteApi(apiName, apiVersion);

        //cy.visit('carbon/user/user-mgt.jsp');
        //cy.deleteUser(publisher);
    })
});